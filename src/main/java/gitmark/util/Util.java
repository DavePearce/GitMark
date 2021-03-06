//BSD 3-Clause License
//
//Copyright (c) 2021, David J. Pearce
//All rights reserved.
//
//Redistribution and use in source and binary forms, with or without
//modification, are permitted provided that the following conditions are met:
//
//1. Redistributions of source code must retain the above copyright notice, this
//   list of conditions and the following disclaimer.
//
//2. Redistributions in binary form must reproduce the above copyright notice,
//   this list of conditions and the following disclaimer in the documentation
//   and/or other materials provided with the distribution.
//
//3. Neither the name of the copyright holder nor the names of its
//   contributors may be used to endorse or promote products derived from
//   this software without specific prior written permission.
//
//THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
//AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
//IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
//DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
//FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
//DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
//SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
//CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
//OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
//OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
package gitmark.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import gitmark.core.Commit;

/**
 * Some general utilities (e.g. for text formatting).
 *
 * @author David J. Pearce
 *
 */
public class Util {
	/**
	 * Construct a string representing a line of text of a given width, where one
	 * component is left justified and the other is right justified. A special
	 * character is used for formatting the space in between.
	 *
	 * @param left
	 * @param c
	 * @param right
	 * @param width
	 * @return
	 */
	public static String toLineString(String left, char c, String right, int width) {
		String r = left + " ";
		width -= left.length() + 1;
		width -= right.length() + 1;
		for (int i = 0; i < width; ++i) {
			r += c;
		}
		r += " ";
		r += right;
		return r;
	}

	/**
	 * Construct a string representing a line of text of a given width made of a
	 * single character. This is useful for creating separators, etc.
	 *
	 * @param c
	 * @param count
	 * @return
	 */
	public static String toLineString(char c, int count) {
		String r = "";
		for (int i = 0; i < count; ++i) {
			r += c;
		}
		return r;
	}


	/**
	 * Checkout all files in a given commit meeting a given criteria (e.g. all Java
	 * source files) into a destination directory.
	 *
	 * @param dir    Working directory into which files should be checked out.
	 * @param commit Commit of files to check ouut
	 * @param filter
	 * @throws IOException
	 */
	public static List<Path> checkout(Path dir, Commit commit, Predicate<String> filter) throws IOException {
		ArrayList<Path> paths = new ArrayList<>();
		for (Commit.Entry e : commit.getEntries()) {
			if (filter.test(e.getPath())) {
				Path path = dir.resolve(e.getPath());
				File parent = path.toFile().getParentFile();
				if (!parent.exists()) {
					parent.mkdirs();
				}
				// Create the file
				Files.createFile(path);
				// Write the file
				Files.write(path, e.getBytesAfter(), StandardOpenOption.TRUNCATE_EXISTING);
				// Add file path relative to working directory
				paths.add(path);
			}
		}
		return paths;
	}

	/**
	 * Delete a directory previously used for a checkout.
	 *
	 * @param dir
	 * @throws IOException
	 */
	public static void delete(Path dir) throws IOException {
		Files.walk(dir).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
	}

	/**
	 * Execute a given command (plus arguments) with a given timeout, whilst reading
	 * all stdin and stdout.
	 *
	 * @param timeout (in milliseconds)
	 * @param cmdargs
	 * @return
	 * @throws IOException
	 */
	public static Result exec(int timeout, List<String> cmdargs) throws IOException {
		ProcessBuilder builder = new ProcessBuilder(cmdargs);
		java.lang.Process child = builder.start();
		InputStream input = child.getInputStream();
		InputStream error = child.getErrorStream();
		try {
			boolean success = child.waitFor(timeout, TimeUnit.MILLISECONDS);
			byte[] stdout = readInputStream(input);
			byte[] stderr = readInputStream(error);
			return new Result(success ? child.exitValue() : null, stdout, stderr);
		} catch (InterruptedException e) {
			byte[] stdout = readInputStream(input);
			byte[] stderr = readInputStream(error);
			return new Result(null, stdout, stderr);
		} finally {
			// make sure child process is destroyed.
			child.destroy();
		}
	}

	/**
	 * Represents the outcome of a given execution. This includes several
	 * components:
	 * <ul>
	 * <li><b>exitCode</b>. This is the exit code for the process which is zero if
	 * successful, or a positive number if an error occurred.</li>
	 * <li><b>stdout</b>. All stdout produced by the process.</li>
	 * <li><b>stderr</b>. All stderr produced by the process (including exception
	 * traces).</li>
	 * </ul>
	 *
	 * @author David J. Pearce
	 *
	 */
	public static class Result {
		/**
		 * Exit code for process, with <code>null</code> indicating timeout occurred.
		 */
		private final Integer exitCode;
		/**
		 * Bytes for the stdout produced.
		 */
		private final byte[] stdout;
		/**
		 * Bytes for the stderr produced.
		 */
		private final byte[] stderr;

		public Result(Integer exitCode, byte[] stdout, byte[] stderr) {
			this.exitCode = exitCode;
			this.stdout = stdout;
			this.stderr = stderr;
		}

		/**
		 * Get the exit code of the process (or <code>null</code> if the process timed
		 * out).
		 *
		 * @return
		 */
		public Integer getExitCode() {
			return exitCode;
		}
		/**
		 * Get all bytes written to <code>stdout</code> during the execution.
		 *
		 * @return
		 */
		public byte[] getStdOut() {
			return stdout;
		}
		/**
		 * Get all bytes written to <code>stderr</code> during the execution.
		 *
		 * @return
		 */
		public byte[] getStdErr() {
			return stderr;
		}
	}


	/**
	 * Read an input stream entirely into a byte array.
	 *
	 * @param input
	 * @return
	 * @throws IOException
	 */
	private static byte[] readInputStream(InputStream input) throws IOException {
		byte[] buffer = new byte[1024];
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		while (input.available() > 0) {
			int count = input.read(buffer);
			output.write(buffer, 0, count);
		}
		return output.toByteArray();
	}
}
