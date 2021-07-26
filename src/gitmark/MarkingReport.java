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

import java.util.Iterator;
import java.util.List;

/**
 * Provides a generic API for extracting key information about the results of
 * marking.
 *
 * @author David J. Pearce
 *
 */
public class MarkingReport implements Iterable<MarkingReport.Mark> {
	private final Commit commit;
	private final List<Mark> marks;

	public MarkingReport(Commit commit, List<Mark> marks) {
		this.commit = commit;
		this.marks = marks;
	}

	public Commit getCommit() {
		return commit;
	}

	@Override
	public Iterator<Mark> iterator() {
		return marks.iterator();
	}

	public int getMark() {
		int total = 0;
		for (Mark m : marks) {
			total += m.getMark();
		}
		return total;
	}

	public int getMaximumMark() {
		int total = 0;
		for (Mark m : marks) {
			total += m.getMaximumMark();
		}
		return total;
	}

	/**
	 * An individual mark awarded by a marking task for a given commit.
	 *
	 * @author David J. Pearce
	 *
	 */
	public static class Mark {
		private final int mark;
		private final int maximum;
		private final String name;
		private final String output;

		public Mark(int mark, int max, String name, String stdout) {
			this.mark = mark;
			this.maximum = max;
			this.name = name;
			this.output = stdout;
		}

		public int getMark() {
			return mark;
		}

		public int getMaximumMark() {
			return maximum;
		}

		public String getName() {
			return name;
		}

		public String getOutput() {
			return output;
		}
	}
}
