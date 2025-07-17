package dev.common.delete;

import java.io.File;

/**
 * ファイル削除ロジック
 * @author shiraishitoshio
 *
 */
public class DeleteFolderInFile {

	/**
	 * ファイル削除
	 * @param filePath ファイルパス
	 */
	public void delete(String filePath) {

		File directory = new File(filePath); // 削除したいフォルダのパス

		// フォルダが存在し、ディレクトリであることを確認
		if (directory.exists() && directory.isDirectory()) {
			// ディレクトリ内のファイル一覧を取得
			File[] files = directory.listFiles();

			if (files != null) {
				for (File file : files) {
					// ファイルまたはサブディレクトリがあれば削除
					if (file.isFile()) {
						forceDelete(file); // ファイルの強制削除
					} else if (file.isDirectory()) {
						forceDeleteDirectory(file); // サブディレクトリの強制削除
					}
				}
			}

			// 最後にディレクトリ自体を削除
			boolean isDeleted = directory.delete();
			if (isDeleted) {
				System.out.println("Force deleted directory: " + directory.getName());
			} else {
				System.out.println("Failed to force delete directory: " + directory.getName());
			}
		} else {
			System.out.println("The provided path is not a directory or does not exist.");
		}


	}

	/**
	 *  ファイルを強制的に削除するメソッド
	 * @param file
	 */
	private static void forceDelete(File file) {
		if (file.exists()) {
			if (file.setWritable(true)) {
				if (file.delete()) {
					System.out.println("Force deleted: " + file.getAbsolutePath());
				} else {
					System.out.println("Failed to delete: " + file.getAbsolutePath());
				}
			} else {
				System.out.println("Unable to set writable permission on: " + file.getAbsolutePath());
			}
		}
	}

	/**
	 *  サブディレクトリを強制的に削除するメソッド
	 * @param directory
	 */
	private static void forceDeleteDirectory(File directory) {
		File[] files = directory.listFiles();
		if (files != null) {
			for (File file : files) {
				if (file.isFile()) {
					forceDelete(file); // ファイルの強制削除
				} else if (file.isDirectory()) {
					forceDeleteDirectory(file); // サブディレクトリを再帰的に削除
				}
			}
		}
		// 最後にサブディレクトリを削除
		if (directory.setWritable(true)) {
			boolean isDeleted = directory.delete();
			if (isDeleted) {
				System.out.println("Force deleted directory: " + directory.getAbsolutePath());
			} else {
				System.out.println("Failed to delete directory: " + directory.getAbsolutePath());
			}
		} else {
			System.out.println("Unable to set writable permission on directory: " + directory.getAbsolutePath());
		}
	}

}
