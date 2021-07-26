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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import org.eclipse.jgit.api.DiffCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;

public class Main {
	private static final int TEXTWIDTH = 80;

	private static final Marker[] MARKERS = {
			c -> Marker.commitSizeMarker(c, 50, 100)
	};

	public static void main(String[] args) throws IOException, NoHeadException, GitAPIException {
		System.out.println("Loading repository from " + args[0]);
		FileRepositoryBuilder builder = new FileRepositoryBuilder();
		Repository repository = builder.setGitDir(new File(args[0])).readEnvironment().findGitDir().build();
		//
		Git git = new Git(repository);
		//
		List<MarkingReport> reports = mark(git, MARKERS);
		// Print report summaries
		for (MarkingReport r : reports) {
			Commit c = r.getCommit();
			String n = c.id.getName();
			System.out.println(Util.toLineString(n, '.', "[" + r.getMark() + " marks]", TEXTWIDTH));
		}
		// Now print report details
		for (MarkingReport r : reports) {
			printReport(r);
		}
	}

	private static void printReport(MarkingReport r) {
		System.out.println();
		System.out.println(Util.toLineString('=',TEXTWIDTH));
		System.out.println("COMMIT: " + r.getCommit().id.getName());
		System.out.println(Util.toLineString('=',TEXTWIDTH));
		System.out.println("\"" + r.getCommit().title + "\"\n");
		// Print task summaries
		for (MarkingReport.Mark m : r) {
			System.out.println(Util.toLineString(m.getName(), ' ', "(" + m.getMark() + " marks)", TEXTWIDTH));
			System.out.println(Util.toLineString('-',TEXTWIDTH));
			String out = m.getOutput();
			if(out != null) {
				System.out.println(out);
			}
		}
	}

	/**
	 * Mark a given repository using a given mechanism for turning commits into
	 * marks.
	 *
	 * @param git    The repository to mark.
	 * @param marker
	 * @throws NoHeadException
	 * @throws GitAPIException
	 * @throws IOException
	 */
	private static List<MarkingReport> mark(Git git, Marker[] tasks)
			throws NoHeadException, GitAPIException, IOException {
		List<Commit> commits = toCommits(git, git.log().call());
		List<MarkingReport> reports = new ArrayList<>();
		// Print out the commits
		for (Commit c : commits) {
			ArrayList<MarkingReport.Mark> marks = new ArrayList<>();
			for (Marker t : tasks) {
				marks.add(t.apply(c).toMark(TEXTWIDTH));
			}
			reports.add(new MarkingReport(c, marks));
		}
		return reports;
	}

	/**
	 * Convert a sequence of commits into a more ammenable form for analysis.
	 *
	 * @param git
	 * @param revs
	 * @return
	 * @throws GitAPIException
	 * @throws IOException
	 */
	private static List<Commit> toCommits(Git git, Iterable<RevCommit> revs) throws GitAPIException, IOException {
		ArrayList<Commit> commits = new ArrayList<>();
		RevCommit last = null;
		for (RevCommit r : revs) {
			if (last != null) {
				commits.add(Commit.toCommit(git, r, last));
			}
			last = r;
		}
		commits.add(Commit.toCommit(git, null, last));
		// Revsere them so oldets comes first.
		Collections.reverse(commits);
		//
		return commits;
	}
}
