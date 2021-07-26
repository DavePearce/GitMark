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

import java.util.function.Function;

/**
 * A mark is responsible for marking a given commit.
 *
 * @author David J. Pearce
 *
 */
public interface Marker extends Function<Commit,Marker.Result> {

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
			for (Commit.Entry e : c.entries) {
				r += Util.toLineString(e.name, ' ', "(" + e.size() + " bytes)", w) + "\n";
			}
			return new MarkingReport.Mark(mark, 1, "Commit size", r);
		};
	}
}
