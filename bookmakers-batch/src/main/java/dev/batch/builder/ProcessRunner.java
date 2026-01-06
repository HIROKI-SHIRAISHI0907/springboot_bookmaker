package dev.batch.builder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * プロセスビルダー
 * @author shiraishitoshio
 *
 */
public class ProcessRunner {

	/**
	 * 実行メソッド
	 * @param pythonBin pythonライブラリ
	 * @param workingDir 実行ファイルディレクトリ
	 * @param scriptName スクリプト
	 * @param args 引数
	 * @return
	 * @throws IOException
	 * @throws InterruptedException
	 */
    public static int run(String pythonBin, Path workingDir, String scriptName, List<String> args)
            throws IOException, InterruptedException {

        List<String> command = new ArrayList<>();
        command.add(pythonBin);
        command.add(scriptName);
        command.addAll(args);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workingDir.toFile());
        pb.redirectErrorStream(true);

        Process p = pb.start();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
            	System.out.println("[PY] " + line);
            }
        }

        return p.waitFor();
    }
}
