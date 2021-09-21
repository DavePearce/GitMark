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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import gitmark.util.OptArg;

public class Main {
	private static final int TEXTWIDTH = 80;
	/**
	 * The amount of time (in ms) for a single task to executed. For example, the
	 * time needed to build all files or run all tests in a given commit.
	 */
	private static final int TIMEOUT = 60000;
	/**
	 * The directory where src files are expected.
	 */
	public static final String JAVA_SRC_DIR = "src";

	private static final Marking.Generator<Boolean> JAVA_BUILD = JavaBuildTask.Generator;
	private static final Marking.Generator<Boolean> JAVA_TEST = JavaTestTask.Generator(TIMEOUT,
			"pacman.server.testing.SinglePlayerTests");
	private static final Marking.Task<Boolean> JAVA_BUILD_TEST = CONTAINER(AND(JAVA_BUILD, JAVA_TEST));

	private static final Marking.Task<Integer> stage1 = IF(GT(INTVAR("Commit Size"), INT(1000)),
			DIV(INTVAR("Commit Size"), INT(-1000)), ZERO);
	private static final Marking.Task<Integer> stage2 = IF(LT(INTVAR("Commit Size"), INT(500)), ONE, stage1);
	// No marks for first commit.
	private static final Marking.Task<Integer> stage3 = LET("Commit Size", ABS(COMMIT_SIZE), stage2);
	private static final Marking.Task<Integer> stage4 = IF(FIRST_COMMIT, ZERO, stage3);
	/**
	 * Put together marking calculation
	 */
	private static final Marking.Task<Integer> MARK = IF(JAVA_BUILD_TEST, stage4, MINUS_ONE);

	public static final OptArg[] DEFAULT_OPTIONS = new OptArg[] {
			new OptArg("help", "Print this help information"),
			new OptArg("verbose", "Print all ouput from tests regardless of pass/fail"),
			new OptArg("last", "Only calculate for last commit") };

	public static void main(String[] _args) throws IOException, NoHeadException, GitAPIException {
		// Process command-line arguments
		ArrayList<String> args = new ArrayList<>(Arrays.asList(_args));
		Map<String, Object> values = OptArg.parseOptions(args, DEFAULT_OPTIONS);
		boolean last = values.containsKey("last");
		String location = ".";
		if(values.containsKey("help")) {
			OptArg.usage(System.out, DEFAULT_OPTIONS);
			return;
		} else if(args.size() > 0){
			location = args.get(0);
		}
		location += File.separator + ".git";
		System.out.println("Loading repository from " + location);
		FileRepositoryBuilder builder = new FileRepositoryBuilder();
		Repository repository = builder.setGitDir(new File(location)).readEnvironment().findGitDir().build();
		//
		Git git = new Git(repository);
		//
		List<Marking.Report> reports = mark(git, last, MARK);
		// Now print report details
		for(int i=0;i!=reports.size();++i) {
			Marking.Report r = reports.get(i);
			printReport(i,r);
		}
		long total = 0;
		// Print report summaries
		System.out.println(Util.toLineString('-', TEXTWIDTH));
		System.out.println("Sumary");
		System.out.println(Util.toLineString('-', TEXTWIDTH));
		for (Marking.Report r : reports) {
			Commit c = r.getCommit();
			String n = c.getObjectId().getName();
			total += r.getResult().getValue();
			System.out.println(Util.toLineString(n, '.', "[" + r.getResult().getValue() + " marks]", TEXTWIDTH));
		}
		System.out.println(Util.toLineString('-', TEXTWIDTH));
		System.out.println("GitMark Total: " + total);
	}

	private static void printReport(int i, Marking.Report r) {
		Marking.Result<Integer> result = r.getResult();
		System.out.println();
		System.out.println();
		System.out.println(Util.toLineString('=',TEXTWIDTH));
		System.out.println(Util.toLineString("COMMIT#" + i + ": " + r.getCommit().getObjectId().getName(), ' ',
				result.getValue() + " marks", TEXTWIDTH));
		System.out.println(Util.toLineString('=',TEXTWIDTH));
		System.out.println("\"" + r.getCommit().getTitle() + "\"\n");
		// Print task summaries
		System.out.println(result.toSummaryString(TEXTWIDTH));
		System.out.println();
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
	private static List<Marking.Report> mark(Git git, boolean last, Marking.Task<Integer> task)
			throws NoHeadException, GitAPIException, IOException {
		List<Commit> commits = toCommits(git, git.log().call());
		List<Marking.Report> results = new ArrayList<>();
		if (last) {
			Commit c = commits.get(commits.size() - 1);
			results.add(new Marking.Report(c, task.apply(c, new HashMap<>())));
		} else {
			// Compute all the marks
			for (Commit c : commits) {
				results.add(new Marking.Report(c, task.apply(c, new HashMap<>())));
			}
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
