//BSD 3-Clause License
//
//Copyright (c) 2021, David Pearce
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
	public static void main(String[] args) throws IOException, NoHeadException, GitAPIException {
		System.out.println("[INFO] Loading repository from " + args[0]);
		FileRepositoryBuilder builder = new FileRepositoryBuilder();
		Repository repository = builder.setGitDir(new File(args[0]))
		  .readEnvironment() // scan environment GIT_* variables
		  .findGitDir() // scan up the file system tree
		  .build();
		//
		Git git = new Git(repository);
		//
		List<Commit> commits = toCommits(git, git.log().call());
		// Print out the commits
		for(Commit c : commits) {
			System.out.println(c.toString());
		}
	}

	private static List<Commit> toCommits(Git git, Iterable<RevCommit> revs) throws GitAPIException, IOException {
		ArrayList<Commit> commits = new ArrayList<>();
		RevCommit last = null;
		for (RevCommit r : revs) {
			if (last != null) {
				commits.add(toCommit(git, r, last));
			}
			last = r;
		}
		commits.add(toCommit(git, null, last));
		// Revsere them so oldets comes first.
		Collections.reverse(commits);
		//
		return commits;
	}

	/**
	 * Convert a given JGit commit into a simpler form for processing.
	 *
	 * @param git
	 * @param before
	 * @param after
	 * @return
	 * @throws GitAPIException
	 * @throws IOException
	 */
	private static Commit toCommit(Git git, RevCommit before, RevCommit after) throws GitAPIException, IOException {
		final Repository repository = git.getRepository();
		AbstractTreeIterator newTreeIter = getTreeIterator(repository, after);
		List<Entry> entries = new ArrayList<>();
		if (before == null) {
			// This is the initial commit, therefore all files in the initial tree are
			// included.
			try (TreeWalk treeWalk = new TreeWalk(repository)) {
				treeWalk.addTree(newTreeIter);
				treeWalk.setRecursive(true);
				// treeWalk.setFilter(filter);
				while (treeWalk.next()) {
					ObjectId oid = treeWalk.getObjectId(0);
					byte[] bytes = repository.open(oid).getBytes();
					entries.add(new Entry(treeWalk.getPathString(), new byte[0], bytes));
				}
			}
		} else {
			// This is not the initial commit, therefore use the default diff algorithm to
			// determine which files are changed.
			AbstractTreeIterator oldTreeIter = getTreeIterator(repository, before);
			List<DiffEntry> diffs = git.diff().setNewTree(newTreeIter).setOldTree(oldTreeIter).call();
			//
			for (DiffEntry e : diffs) {
				entries.add(toEntry(repository, e));
			}
		}
		return new Commit(after.getId(), after.getShortMessage(), entries);
	}

	/**
	 * Convert a given DiffEntry into a form which is a little more ammeanable for
	 * this analysis.
	 *
	 * @param repository
	 * @param e
	 * @return
	 * @throws LargeObjectException
	 * @throws MissingObjectException
	 * @throws IOException
	 */
	private static Entry toEntry(Repository repository, DiffEntry e)
			throws LargeObjectException, MissingObjectException, IOException {
		final ObjectId oid = e.getOldId().toObjectId();
		final ObjectId nid = e.getNewId().toObjectId();
		byte[] oldBytes;
		byte[] newBytes;
		String path;
		switch (e.getChangeType()) {
		case ADD:
		case COPY: {
			path = e.getPath(DiffEntry.Side.NEW);
			oldBytes = new byte[0];
			newBytes = repository.open(nid).getBytes();
			break;
		}
		case DELETE: {
			path = e.getPath(DiffEntry.Side.OLD);
			oldBytes = repository.open(oid).getBytes();
			newBytes = new byte[0];
			break;
		}
		case MODIFY: {
			path = e.getPath(DiffEntry.Side.NEW);
			newBytes = repository.open(nid).getBytes();
			oldBytes = repository.open(oid).getBytes();
			break;
		}
		default: {
			return null;
		}
		}
		return new Entry(path,oldBytes,newBytes);
	}

	private static AbstractTreeIterator getTreeIterator(Repository repository, RevCommit commit) throws IOException {
		try (RevWalk walk = new RevWalk(repository)) {
			// Extract tree associated with this particular commit.
			RevTree tree = walk.parseTree(commit.getTree().getId());
			// Construct tree parser
			CanonicalTreeParser treeParser = new CanonicalTreeParser(null, repository.newObjectReader(), tree.getId());
			walk.dispose();
			return treeParser;
		}
	}

	private static class Commit {
		public final ObjectId id;
		public final String title;
		public final List<Entry> entries;

		public Commit(ObjectId id, String title, List<Entry> entries) {
			this.id = id;
			this.title = title;
			this.entries = entries;
		}

		@Override
		public String toString() {
			String s = id.getName() + ";\"" + title + "\"";
			for(int i=0;i!=entries.size();++i) {
				s += ";" + entries.get(i);
			}
			return s;
		}
	}

	private static class Entry {
		public final String name;
		public final byte[] before;
		public final byte[] after;

		public Entry(String n, byte[] before, byte[] after) {
			if(before == null || after == null) {
				throw new IllegalArgumentException("invalid parameters");
			}
			this.name = n;
			this.before = before;
			this.after = after;
		}

		public int size() {
			return after.length - before.length;
		}

		@Override
		public String toString() {
			String n = "\"" + name + "\"";
			int size = size();
			return (size < 0) ? n + size : n + "+" + size;
		}
	}
}
