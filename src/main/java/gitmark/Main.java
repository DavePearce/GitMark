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
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import gitmark.core.Commit;
import gitmark.core.Marking;
import gitmark.tasks.JavaBuildTask;
import gitmark.tasks.JavaTestTask;

import static gitmark.core.Marking.*;

import gitmark.util.Util;

public class Main {
	private static final int TEXTWIDTH = 80;
	private static final int TIMEOUT = 1000;

	private static final Marking.Generator<Boolean> JAVA_BUILD = JavaBuildTask.Generator;
	private static final Marking.Generator<Boolean> JAVA_TEST = JavaTestTask.Generator(TIMEOUT,
			"pacman.server.testing.SinglePlayerTests");
	private static final Marking.Task<Boolean> JAVA_BUILD_TEST = CONTAINER(AND(JAVA_BUILD, JAVA_TEST));

	private static final Marking.Task<Integer> MARK = IF(JAVA_BUILD_TEST, IF(LT(COMMIT_SIZE, INT(100)), ONE, ZERO),
			MINUS_ONE);

	public static void main(String[] args) throws IOException, NoHeadException, GitAPIException {
		System.out.println("Loading repository from " + args[0]);
		FileRepositoryBuilder builder = new FileRepositoryBuilder();
		Repository repository = builder.setGitDir(new File(args[0])).readEnvironment().findGitDir().build();
		//
		Git git = new Git(repository);
		//
		List<Marking.Report> reports = mark(git, MARK);
		// Print report summaries
		for (Marking.Report r : reports) {
			Commit c = r.getCommit();
			String n = c.getObjectId().getName();
			System.out.println(Util.toLineString(n, '.', "[" + r.getResult().getValue() + " marks]", TEXTWIDTH));
		}
		// Now print report details
		for (Marking.Report r : reports) {
			printReport(r);
		}
	}

	private static void printReport(Marking.Report r) {
		Marking.Result<Integer> result = r.getResult();
		System.out.println();
		System.out.println(Util.toLineString('=',TEXTWIDTH));
		System.out.println(Util.toLineString("COMMIT: " + r.getCommit().getObjectId().getName(), ' ',
				result.getValue() + " marks", TEXTWIDTH));
		System.out.println(Util.toLineString('=',TEXTWIDTH));
		System.out.println("\"" + r.getCommit().getTitle() + "\"\n");
		// Print task summaries
		System.out.println(result.toSummaryString(TEXTWIDTH));
		// Print provenance
		System.out.println(result.toProvenanceString(TEXTWIDTH));
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
	private static List<Marking.Report> mark(Git git, Marking.Task<Integer> task)
			throws NoHeadException, GitAPIException, IOException {
		List<Commit> commits = toCommits(git, git.log().call());
		List<Marking.Report> results = new ArrayList<>();
		// Compute all the marks
		for (Commit c : commits) {
			results.add(new Marking.Report(c, task.apply(c)));
		}
		return results;
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
