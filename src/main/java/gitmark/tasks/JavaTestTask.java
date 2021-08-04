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
package gitmark.tasks;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;

import gitmark.core.Commit;
import gitmark.core.Marking;
import gitmark.core.Marking.Task;
import gitmark.util.Pair;
import gitmark.util.ProcessTimerMethod;
import gitmark.util.Util;

public class JavaTestTask implements Marking.Task<Boolean> {
	private final Path dir;
	private final String[] testClassNames;
	private final int timeout;

	public JavaTestTask(int timeout, Path dir, String... testClassNames) {
		this.dir = dir;
		this.timeout = timeout;
		this.testClassNames = testClassNames;
	}

	@Override
	public String getName() {
		return "Java Test";
	}

	@Override
	public gitmark.core.Marking.Result<Boolean> apply(Commit c) throws IOException {
		String classpath = System.getProperty("java.class.path");
		ProcessTimerMethod tm = new ProcessTimerMethod(
				classpath + File.pathSeparator + dir.toString() + File.separator + "src");
		Result[] results = new Result[testClassNames.length];
		// First, execute all test cases and gather the results together.
		for (int i = 0; i != testClassNames.length; ++i) {
			String testClassName = testClassNames[i];
			results[i] = executeTestCases(tm, testClassName);
		}
		boolean passed = checkOutcome(results);
		//
		return new gitmark.core.Marking.Result<Boolean>() {
			@Override
			public Boolean getValue() {
				return passed;
			}

			@Override
			public String toSummaryString(int width) {
				return "";
			}

			@Override
			public String toProvenanceString(int width) {
				String result = Util.toLineString('-', width);
				result += "\nJava Test:\n\n";
				for(Result r : results) {
					result += JavaTestTask.toProvenanceString(width, r);
				}
				return result;
			}
		};
	}

	public static Marking.Generator<Boolean> Generator(int timeout, String... testnames) {
		return new Marking.Generator<Boolean>() {

			@Override
			public String getName() {
				return "Java Test";
			}

			@Override
			public Task<Boolean> apply(Path dir) throws IOException {
				return new JavaTestTask(timeout, dir, testnames);
			}
		};
	}

	/**
	 * Count the number of successful tests in a given array of tests
	 *
	 * @param results
	 * @return
	 */
	private boolean checkOutcome(Result[]... results) {
		for (Result[] inner : results) {
			for (Result r : inner) {
				if (r.outcome != ExitCode.OK) {
					return false;
				} else {
					Pair<String, String>[] result = (Pair[]) r.result.getReturnAs(Pair[].class);
					for(Pair<String,String> ith : result ) {
						if(ith.second() != null) {
							return false;
						}
					}
				}
			}
		}
		return true;
	}
	/**
	 * Execute all tests contained within a given test class, producing a result
	 * for each test case.
	 *
	 * @param testClass
	 *            The class containing test cases to execute
	 * @return
	 */
	private Result executeTestCases(ProcessTimerMethod ptm, String testClassName) {
		String stdout;
		String stderr;
		ExitCode outcome;
		ProcessTimerMethod.Outcome r = null;
		try {
			r = ptm.exec(timeout, JavaTestTask.class.getName(), "runTests", testClassName);
			stdout = new String(r.getStdout());
			stderr = new String(r.getStderr());
			if (r.exitCode() == null) {
				outcome = ExitCode.TIMEOUT;
			} else if (r.exitCode() == 0) {
				outcome = ExitCode.OK;
			} else {
				outcome = ExitCode.FAILED;
			}
		} catch (Throwable e) {
			// Some kind of exception occurred. What should we do with the
			// exception itself?
			outcome = ExitCode.EXCEPTION;
			stdout = null;
			stderr = e.getMessage();
		}
		return new Result(testClassName, outcome, stdout, stderr, r);
	}

	public static Pair<String,String>[] runTests(String testClassName) throws Throwable {
		Class<?> testClass = Class.forName(testClassName);
		Object o = testClass.newInstance();
		Method[] tests = findTestCases(testClass);
		Pair<String,String>[] results = new Pair[tests.length];
		for (int i = 0; i != tests.length; ++i) {
			Method test = tests[i];
			String error = null;
			try {
				test.invoke(o);
			} catch(InvocationTargetException e) {
				error = e.getCause().getMessage();
			}
			results[i] = new Pair<>(test.getName(), error);
		}
		//
		return results;
	}

	private static Method[] findTestCases(Class<?> testClass) {
		ArrayList<Method> tests = new ArrayList<>();
		for (Method m : testClass.getMethods()) {
			if (m.isAnnotationPresent(org.junit.Test.class)) {
				// JUnit 4
				tests.add(m);
			} else if (m.isAnnotationPresent(org.junit.jupiter.api.Test.class)
					&& !m.isAnnotationPresent(org.junit.jupiter.api.Disabled.class)) {
				// JUnit 5
				tests.add(m);
			}
		}
		return tests.toArray(new Method[tests.size()]);
	}

	private enum ExitCode {
		OK,
		FAILED,
		TIMEOUT,
		EXCEPTION
	}

	private class Result {
		/**
		 * Name of the test case in question
		 */
		public final String name;

		/**
		 * Indicates whether the test was successful or not.
		 */
		public final ExitCode outcome;

		/**
		 * The stdout produced from executing the test case.
		 */
		public final String stdout;

		/**
		 * The stderr produced from executing the test case.
		 */
		public final String stderr;

		public final ProcessTimerMethod.Outcome result;

		public Result(String name, ExitCode outcome, String stdout, String stderr,ProcessTimerMethod.Outcome result) {
			this.name = name;
			this.outcome = outcome;
			this.stdout = stdout;
			this.stderr = stderr;
			this.result = result;
		}
	}

	private static String toProvenanceString(int width, Result r) {
		String str = "";
		if (r.outcome != ExitCode.OK) {
			return r.name + " ... FAILED";
		} else {
			Pair<String, String>[] result = (Pair[]) r.result.getReturnAs(Pair[].class);
			int count = 0;
			for(Pair<String,String> ith : result ) {
				if(ith.second() != null) {
					str += Util.toLineString(ith.first(),'.',"FAILED", width) + "\n";
					str += ith.second();
				} else {
					count++;
				}
			}
			return str + count + " / " + result.length + " tests passed.\n";
		}
	}
}
