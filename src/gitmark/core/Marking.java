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
		String toString(int width);

		T getValue();
	}

	/**
	 * Represents the outcome of a marking task which produces a boolean yes/no. For
	 * example, did the build succeed?
	 *
	 * @author David J. Pearce
	 *
	 */
	public abstract static class BooleanResult implements Result<Boolean> {
		private final boolean value;

		public BooleanResult(boolean v) {
			this.value = v;
		}

		@Override
		public Boolean getValue() {
			return value;
		}
	}

	/**
	 * Represents the outcome of marking task which produces a integer value of some
	 * kind. For example, how many files were modified in this commit?
	 *
	 * @author David J. Pearce
	 *
	 */
	public abstract static class IntegerResult implements Result<Integer> {
		private final int value;

		public IntegerResult(int v) {
			this.value = v;
		}

		@Override
		public Integer getValue() {
			return value;
		}
	}

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

	public static final Task<Integer> ZERO = new Task<Integer>() {

		@Override
		public String getName() {
			return "(0 marks)";
		}

		@Override
		public Result<Integer> apply(Commit c) throws IOException {
			return new IntegerResult(0) {

				@Override
				public String toString(int width) {
					return "???";
				}

			};
		}

	};

	public static final Task<Integer> ONE = new Task<Integer>() {

		@Override
		public String getName() {
			return "(1 mark)";
		}

		@Override
		public Result<Integer> apply(Commit c) throws IOException {
			return new IntegerResult(0) {

				@Override
				public String toString(int width) {
					return "???";
				}

			};
		}

	};

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
			public Result<T> apply(Commit c) throws IOException {
				Result<Boolean> r = condition.apply(c);
				// FIXME: we've lost something here!
				if (r.getValue()) {
					return yes.apply(c);
				} else {
					return no.apply(c);
				}
			}
		};
	}
}
