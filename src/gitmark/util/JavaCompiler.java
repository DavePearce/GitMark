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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class JavaCompiler {
	private int timeout = 10000; // 10s timeout
	private String command = "javac";
	private List<String> classpath = new ArrayList<>();
	private Path srcdir = null;
	private Path bindir = null;
	private boolean nowarn = false;

	public List<String> classpath() {
		return classpath;
	}

	/**
	 * Toggle flag to indicate whether to report warnings or not.
	 *
	 * @param flag
	 * @return
	 */
	public JavaCompiler setWarnings(boolean flag) {
		nowarn = !flag;
		return this;
	}

	/**
	 * Set the timeout for this command in ms.
	 *
	 * @param ms
	 * @return
	 */
	public JavaCompiler setTimout(int ms) {
		this.timeout = ms;
		return this;
	}
	/**
	 * Specify the root directed where source files should be located.
	 *
	 * @param srcdir
	 */
	public JavaCompiler setSourceDirectory(Path srcdir) {
		this.srcdir = srcdir;
		return this;
	}
	/**
	 * Specify the root directory where generated class files should be located.
	 *
	 * @param srcdir
	 */
	public JavaCompiler setClassDirectory(Path bindir) {
		this.bindir = bindir;
		return this;
	}

	public Util.Result compile(List<Path> files) throws IOException {
		ArrayList<String> args = new ArrayList<>();
		args.add(command);
		if(nowarn) {
			args.add("-nowarn");
		}
		if(srcdir != null) {
			args.add("-s");
			args.add(srcdir.toString());
		}
		if(bindir != null) {
			args.add("-d");
			args.add(bindir.toString());
		}
		if(classpath.size() > 0) {
			args.add("-cp");
			args.add(toPathString(classpath));
		}
		for (Path p : files) {
			args.add(p.toString());
		}
		System.out.println("GOT: " + args);
		return Util.exec(timeout, args);
	}

	private static String toPathString(List<String> path) {
		String r = "";
		for (int i = 0; i != path.size(); ++i) {
			if (i != 0) {
				r += ";";
			}
			r += path.get(i);
		}
		return r;
	}
}
