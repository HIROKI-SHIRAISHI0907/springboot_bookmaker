package dev.common.zip;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * mid単位のzipアーカイブに対する「zip化」「解凍」をまとめて扱うクラス。
 *
 * <p>
 * バケットにはmid単位でまとめられたCSV群が {@code mid名.zip} というファイル名で格納される。
 * 追加でCSVを格納する場合は、
 * </p>
 * <ol>
 *   <li>既存の {@code mid名.zip} をダウンロードし {@link #decompress(Path, Path)} で解凍</li>
 *   <li>解凍先ディレクトリに新しいCSVを追加</li>
 *   <li>{@link #compress(Path, Path)} で同じファイル名(mid名.zip)として再度zip化してアップロード</li>
 * </ol>
 * <p>
 * という手順を踏むため、利用者からは同じzipファイルのままに見えるが、中身のCSVは増えていく。
 * zip化・解凍のどちらも本クラスに実装する。
 * </p>
 */
@Slf4j
@Component
public class ZipArchiveHandler {

	private static final int BUFFER_SIZE = 8192;

	/**
	 * sourceDir配下の全ファイルをzip化し、targetZipFileに出力する。
	 * zip内のエントリ名は sourceDir からの相対パス("/"区切り)とし、
	 * エントリはパス文字列の昇順で格納する。
	 *
	 * @param sourceDir     zip化対象のルートディレクトリ
	 * @param targetZipFile 出力先zipファイルパス(既に存在する場合は上書きする)
	 */
	public void compress(Path sourceDir, Path targetZipFile) throws IOException {
		if (sourceDir == null || !Files.isDirectory(sourceDir)) {
			throw new IOException("圧縮対象ディレクトリが不正です: " + sourceDir);
		}
		if (targetZipFile == null) {
			throw new IOException("出力先zipファイルパスがnullです");
		}
		if (targetZipFile.getParent() != null) {
			Files.createDirectories(targetZipFile.getParent());
		}

		List<Path> targetFiles;
		try (Stream<Path> walk = Files.walk(sourceDir)) {
			targetFiles = walk
					.filter(Files::isRegularFile)
					.sorted(Comparator.comparing(p -> toRelativeEntryName(sourceDir, p)))
					.collect(Collectors.toList());
		}

		Path tmpZip = Paths.get(targetZipFile.toString() + ".tmp");
		try (OutputStream fos = Files.newOutputStream(tmpZip);
				BufferedOutputStream bos = new BufferedOutputStream(fos, BUFFER_SIZE);
				ZipOutputStream zos = new ZipOutputStream(bos)) {
			byte[] buffer = new byte[BUFFER_SIZE];
			for (Path file : targetFiles) {
				String entryName = toRelativeEntryName(sourceDir, file);
				zos.putNextEntry(new ZipEntry(entryName));
				try (InputStream is = Files.newInputStream(file);
						BufferedInputStream bis = new BufferedInputStream(is, BUFFER_SIZE)) {
					int len;
					while ((len = bis.read(buffer)) != -1) {
						zos.write(buffer, 0, len);
					}
				}
				zos.closeEntry();
			}
		}
		Files.move(tmpZip, targetZipFile, StandardCopyOption.REPLACE_EXISTING);
		log.info("[ZipArchiveHandler] compress done. sourceDir={}, targetZipFile={}, files={}",
				sourceDir, targetZipFile, targetFiles.size());
	}

	/**
	 * zipファイルをtargetDir配下に解凍する。
	 * zip-slip対策として、解凍先がtargetDirの外に出るエントリは無視する。
	 *
	 * @param zipFile   解凍対象zipファイル
	 * @param targetDir 解凍先ディレクトリ(存在しなければ作成する)
	 */
	public void decompress(Path zipFile, Path targetDir) throws IOException {
		if (zipFile == null || !Files.isRegularFile(zipFile)) {
			throw new IOException("解凍対象zipファイルが存在しません: " + zipFile);
		}
		if (targetDir == null) {
			throw new IOException("解凍先ディレクトリパスがnullです");
		}
		Files.createDirectories(targetDir);
		Path normalizedTargetDir = targetDir.normalize().toAbsolutePath();

		try (InputStream fis = Files.newInputStream(zipFile);
				BufferedInputStream bis = new BufferedInputStream(fis, BUFFER_SIZE);
				ZipInputStream zis = new ZipInputStream(bis)) {
			byte[] buffer = new byte[BUFFER_SIZE];
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				Path entryPath = normalizedTargetDir.resolve(entry.getName()).normalize();
				if (!entryPath.startsWith(normalizedTargetDir)) {
					log.warn("[ZipArchiveHandler] zip-slipの可能性があるためスキップ: entry={}", entry.getName());
					zis.closeEntry();
					continue;
				}
				if (entry.isDirectory()) {
					Files.createDirectories(entryPath);
				} else {
					if (entryPath.getParent() != null) {
						Files.createDirectories(entryPath.getParent());
					}
					try (OutputStream fos = Files.newOutputStream(entryPath);
							BufferedOutputStream bos = new BufferedOutputStream(fos, BUFFER_SIZE)) {
						int len;
						while ((len = zis.read(buffer)) != -1) {
							bos.write(buffer, 0, len);
						}
					}
				}
				zis.closeEntry();
			}
		}
		log.info("[ZipArchiveHandler] decompress done. zipFile={}, targetDir={}", zipFile, targetDir);
	}

	private String toRelativeEntryName(Path baseDir, Path file) {
		String relative = baseDir.relativize(file).toString();
		return relative.replace('\\', '/');
	}
}