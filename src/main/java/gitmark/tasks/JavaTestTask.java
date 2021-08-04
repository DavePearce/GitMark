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

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;

import gitmark.core.Commit;
import gitmark.core.Marking;
import gitmark.util.ProcessTimerMethod;

public class JavaTestTask implements Marking.Task<Boolean> {
	private final String[] testClassNames;
	private final int timeout;

	public JavaTestTask(int timeout, String... testClassNames) {
		this.timeout = timeout;
		this.testClassNames = testClassNames;
	}

	@Override
	public String getName() {
		return "Java Test";
	}

	@Override
	public gitmark.core.Marking.Result<Boolean> apply(Commit c) throws IOException {
		try {
			Result[][] results = new Result[testClassNames.length][];
			// First, execute all test cases and gather the results together.
			for (int i = 0; i != testClassNames.length; ++i) {
				String testClassName = testClassNames[i];
				Class<?> testClass = Class.forName(testClassName);
				results[i] = executeTestCases(testClass);
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
					return "???";
				}

				@Override
				public String toProvenanceString(int width) {
					return "empty";
				}
			};
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
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
	private Result[] executeTestCases(Class<?> testClass) {
		String testClassName = testClass.getName();
		String[] tests = findTestCases(testClass);
		Result[] results = new Result[tests.length];
		for (int i = 0; i != tests.length; ++i) {
			String test = tests[i];
			String stdout;
			String stderr;
			ExitCode outcome;
			try {
				ProcessTimerMethod.Outcome r = ProcessTimerMethod.exec(timeout, testClassName, test);
				stdout = new String(r.getStdout());
				stderr = new String(r.getStderr());
				if(r.exitCode() == null) {
					outcome = ExitCode.TIMEOUT;
				} else if(r.exitCode() == 0) {
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
			results[i] = new Result(test, outcome, stdout, stderr);
		}
		//
		return results;
	}

	private String[] findTestCases(Class<?> testClass) {
		ArrayList<String> tests = new ArrayList<>();
		for (Method m : testClass.getMethods()) {
			if (m.isAnnotationPresent(org.junit.Test.class)) {
				// JUnit 4
				tests.add(m.getName());
			} else if(m.isAnnotationPresent(org.junit.jupiter.api.Test.class)) {
				// JUnit 5
				tests.add(m.getName());
			}
		}
		String[] ts = tests.toArray(new String[tests.size()]);
		Arrays.sort(ts);
		return ts;
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

		public Result(String name, ExitCode outcome, String stdout, String stderr) {
			this.name = name;
			this.outcome = outcome;
			this.stdout = stdout;
			this.stderr = stderr;
		}
	}
}
