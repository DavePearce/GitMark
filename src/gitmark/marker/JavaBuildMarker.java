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
package gitmark.marker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import gitmark.core.Commit;
import gitmark.core.Marking;
import gitmark.core.Marking.BooleanResult;
import gitmark.util.JavaCompiler;
import gitmark.util.Util;

public class JavaBuildMarker implements Marking.Task<Boolean> {

	@Override
	public String getName() {
		return "Java Build";
	}

	@Override
	public Marking.Result<Boolean> apply(Commit c) throws IOException {
		// Create temporary directory for checkout.
		Path dir = Files.createTempDirectory("gitmark");
		//
		try {
			// Checkout all Java source files
			List<Path> files = Util.checkout(dir, c, s -> s.endsWith(".java"));
			// Attempt to build Java source files
			JavaCompiler javac = new JavaCompiler();
			javac.setWarnings(false);
			javac.setSourceDirectory(dir);
			Util.Result result = javac.compile(files);
			Integer exitCode = result.getExitCode();
			// Determine whether build was successful or not.
			final boolean success = (exitCode != null && exitCode == 0);
			return new BooleanResult(success) {
				@Override
				public String toString(int width) {
					String r = "";
					String out = new String(result.getStdOut());
					String err = new String(result.getStdErr());
					if (exitCode == null) {
						r += "\n(timeout)\n";
					}
					if (out.length() > 0) {
						r += "\n";
						r += out;
					}
					if (err.length() > 0) {
						r += "\n";
						r += err;
					}
					return r;
				}
			};
		} finally {
			// No matter what, delete the build directory
			Util.delete(dir);
		}
	}
}
