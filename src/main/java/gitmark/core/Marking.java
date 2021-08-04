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
package gitmark.core;

import java.io.IOException;
import java.util.function.Function;

import gitmark.tasks.JavaBuildTask;
import gitmark.util.Util;

public class Marking {
	/**
	 * A marking task is something which needs to be done in order to produce a
	 * marking result. For example, a task might be to build the code at a specific
	 * commit in a repository.
	 *
	 * @author David J. Pearce
	 *
	 * @param <T>
	 */
	public interface Task<T> {
		/**
		 * Get the name of this task. This is used for reporting purposes only.
		 *
		 * @return
		 */
		public String getName();

		/**
		 * Apply this task to a given commit, producing a given result.
		 *
		 * @param c
		 * @return
		 * @throws IOException
		 */
		public Result<T> apply(Commit c) throws IOException;
	}

	/**
	 * An unformatted result from a marker which may additionally contain some kind
	 * of payload.
	 *
	 * @author David J. Pearce
	 *
	 */
	public interface Result<T> {
		String toSummaryString(int width);
		String toProvenanceString(int width);
		T getValue();
	}

	/**
	 * Represents an actual mark awarded for some component of the assessment for a
	 * given commit. The provenance of the mark is determined through the result.
	 *
	 * @author David J. Pearce
	 *
	 */
	public static class Report {
		private final Commit commit;
		private final Result<Integer> result;

		public Report(Commit c, Result<Integer> result) {
			this.commit = c;
			this.result = result;
		}

		public Commit getCommit() {
			return commit;
		}

		public Result<Integer> getResult() {
			return result;
		}
	}

	public static final Task<Integer> ZERO = INT(0);
	public static final Task<Integer> ONE = INT(1);
	public static final Task<Integer> MINUS_ONE = INT(-1);

	public static final Task<Integer> INT(int v) {
		return new Task<Integer>() {

			@Override
			public String getName() {
				return Integer.toString(v);
			}

			@Override
			public Result<Integer> apply(Commit c) throws IOException {
				return new Result<Integer>() {
					@Override
					public String toSummaryString(int width) {
						return "= " + Integer.toString(v) + " mark(s).";
					}

					@Override
					public Integer getValue() {
						return v;
					}

					@Override
					public String toProvenanceString(int width) {
						return "";
					}
				};
			}
		};
	}

	/**
	 * Construct a marking task composed from other tasks which implements a
	 * conditional.
	 *
	 * @param <T>
	 * @param condition
	 * @param yes
	 * @param no
	 * @return
	 */
	public static <T> Task<T> IF(Task<Boolean> condition, Task<T> yes, Task<T> no) {
		return new Task<T>() {
			@Override
			public String getName() {
				return "If " + condition.getName();
			}

			@Override
			public Result<T> apply(Commit commit) throws IOException {
				Result<Boolean> c = condition.apply(commit);
				Result<T> r;
				if (c.getValue()) {
					r = yes.apply(commit);
				} else {
					r = no.apply(commit);
				}
				return new Result<T>() {
					@Override
					public String toSummaryString(int width) {
						String b = c.getValue() ? "Yes." : "No.";
						String str = condition.getName() + "? " + b + "\n";
						return str + r.toSummaryString(width);
					}

					@Override
					public T getValue() {
						return r.getValue();
					}

					@Override
					public String toProvenanceString(int width) {
						return c.toProvenanceString(width) + r.toProvenanceString(width);
					}
				};
			}
		};
	}

	/**
	 * Construct a marking task composed from other tasks which implements a
	 * conditional.
	 *
	 * @param <T>
	 * @param condition
	 * @param yes
	 * @param no
	 * @return
	 */
	public static Task<Boolean> AND(Task<Boolean> lhs, Task<Boolean> rhs) {
		return new Task<Boolean>() {
			@Override
			public String getName() {
				return lhs.getName() + " && " + rhs.getName();
			}

			@Override
			public Result<Boolean> apply(Commit commit) throws IOException {
				Result<Boolean> c = lhs.apply(commit);
				Result<Boolean> r;
				if (c.getValue()) {
					r = rhs.apply(commit);
				} else {
					r = c;
				}
				return new Result<Boolean>() {
					@Override
					public String toSummaryString(int width) {
						String b = c.getValue() ? "Yes." : "No.";
						String str = lhs.getName() + "? " + b + "\n";
						return str + r.toSummaryString(width);
					}

					@Override
					public Boolean getValue() {
						return r.getValue();
					}

					@Override
					public String toProvenanceString(int width) {
						return c.toProvenanceString(width) + r.toProvenanceString(width);
					}
				};
			}
		};
	}

	/**
	 * A simple marking task which checks whether the outcome of one task is lower
	 * than another.
	 *
	 * @param lhs
	 * @param rhs
	 * @return
	 */
	public static final Marking.Task<Boolean> LT(Task<Integer> lhs, Task<Integer> rhs) {
		return new Task<Boolean>() {
			@Override
			public String getName() {
				return lhs.getName() + " < " + rhs.getName();
			}

			@Override
			public Result<Boolean> apply(Commit c) throws IOException {
				Result<Integer> l = lhs.apply(c);
				Result<Integer> r = rhs.apply(c);
				return new Result<Boolean>() {
					@Override
					public Boolean getValue() {
						return l.getValue() < r.getValue();
					}
					@Override
					public String toSummaryString(int width) {
						return l.toString() + " < " + r.toString();
					}

					@Override
					public String toProvenanceString(int width) {
						return l.toProvenanceString(width) + r.toProvenanceString(width);
					}
				};
			}
		};
	}

	/**
	 * A simple marking task which returns the size of a commit (in bytes).
	 */
	public static final Marking.Task<Integer> COMMIT_SIZE = new Marking.Task<Integer>() {

		@Override
		public String getName() {
			return "Commit Size";
		}

		@Override
		public Marking.Result<Integer> apply(Commit c) throws IOException {
			final int size = (int) c.size();
			return new Marking.Result<Integer>() {
				@Override
				public Integer getValue() {
					return size;
				}

				@Override
				public String toSummaryString(int w) {
					return "";
				}

				@Override
				public String toProvenanceString(int w) {
					String r = Util.toLineString('-', w);
					r += "\nCommit size:\n\n";
					int total = 0;
					for (Commit.Entry e : c.getEntries()) {
						total += e.size();
						if (e.changed()) {
							r += Util.toLineString(e.getPath(), ' ', e.size() + " bytes", w) + "\n";
						}
					}
					r += Util.toLineString("", ' ', "= " + total + " bytes", w) + "\n";
					return r;
				}
			};
		}
	};
}
