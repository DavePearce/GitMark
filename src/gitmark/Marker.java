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
package gitmark;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;

/**
 * A mark is responsible for marking a given commit.
 *
 * @author David J. Pearce
 *
 */
public interface Marker {

	public Result apply(Commit c) throws IOException;

	public interface Result {
		MarkingReport.Mark toMark(int width);
	}

	/**
	 * Construct a marker which awards marks based on the size of a given commit (in
	 * bytes). For commits below a certain minimum size, one mark is awarded.
	 *
	 * @param c
	 * @param min The maximum size a commit can be to obtain a mark.
	 * @param max The point at which a commit receives negatives marks.
	 * @return
	 */
	public static Result commitSizeMarker(Commit c, int min, int max) {
		// Mark the commit
		int mark;
		long size = c.size();
		if (size < min) {
			mark = 1;
		} else if (size < max) {
			mark = 0;
		} else {
			mark = -1;
		}
		// Return the result
		return w -> {
			String r = "";
			for (Commit.Entry e : c.getEntries()) {
				r += Util.toLineString(e.getPath(), ' ', "(" + e.size() + " bytes)", w) + "\n";
			}
			return new MarkingReport.Mark(mark, 1, "Commit size", r);
		};
	}

	/**
	 * Construct a marker which determines whether or not a given commit builds
	 * using javac. This is done by checking out the contents of the repository
	 * after the commit into a temporary directory and trying to build it.
	 *
	 * @param c
	 * @return
	 * @throws IOException
	 */
	public static Result javaBuildMarker(Commit c) throws IOException {
		// Create temporary directory for checkout.
		Path dir = Files.createTempDirectory("gitmark");
		//
		try {
			int count = 0;
			for (Commit.Entry e : c.getEntries()) {
				Path path = dir.resolve(e.getPath());
				File parent = path.toFile().getParentFile();
				if (parent.exists() || parent.mkdirs()) {
					count = count + 1;
					System.out.println("WRITING: " + path);
					// Create the file
					Files.createFile(path);
					// Write the file
					Files.write(path, e.getBytesAfter(), StandardOpenOption.TRUNCATE_EXISTING);
				} else {
					throw new RuntimeException("PROBLEM: " + parent);
				}
			}
			final int _count = count;
			return w -> {
				String r = "Compiled " + _count + " file(s).";
				return new MarkingReport.Mark(0, 0, "Java Compile", r);
			};
		} finally {
			// No matter what, delete the directory
			//Files.walk(dir).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
		}
	}
}
