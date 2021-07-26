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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * Provides a simple API for extracting information from a commit. In effect, it
 * is a wrapper for JGit's <code>RevCommit</code>, just packaging what I want
 * together in one place.
 *
 * @author David J. Pearcde
 *
 */
public class Commit implements Iterable<Commit.Entry> {
	private final ObjectId id;
	private final String title;
	private final List<Entry> entries;

	public Commit(ObjectId id, String title, List<Entry> entries) {
		this.id = id;
		this.title = title;
		this.entries = entries;
	}

	public long size() {
		long s = 0;
		for(int i=0;i!=getEntries().size();++i) {
			s += getEntries().get(i).size();
		}
		return s;
	}

	public ObjectId getObjectId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public List<Entry> getEntries() {
		return entries;
	}

	@Override
	public Iterator<Entry> iterator() {
		return entries.iterator();
	}

	@Override
	public String toString() {
		String s = id.getName() + ";\"" + getTitle() + "\"";
		for(int i=0;i!=getEntries().size();++i) {
			s += ";" + getEntries().get(i);
		}
		return s;
	}

	/**
	 * Represents an entry within a commit.
	 *
	 * @author David J. Pearce
	 *
	 */
	public static class Entry {
		private final String path;
		private final byte[] before;
		private final byte[] after;

		public Entry(String path, byte[] before, byte[] after) {
			if(before == null || after == null) {
				throw new IllegalArgumentException("invalid parameters");
			}
			this.path = path;
			this.before = before;
			this.after = after;
		}

		public int size() {
			return getBytesAfter().length - getBytesBefore().length;
		}

		/**
		 * Get the path of this entry in the repository.
		 *
		 * @return
		 */
		public String getPath() {
			return path;
		}

		/**
		 * Get the bytes of this file as it was <i>before</i> the commit in question.
		 *
		 * @return
		 */
		public byte[] getBytesBefore() {
			return before;
		}

		/**
		 * Get the bytes of this file as it was <i>after<i> the commit in question.
		 *
		 * @return
		 */
		public byte[] getBytesAfter() {
			return after;
		}

		@Override
		public String toString() {
			String n = getPath() + " (";
			int size = size();
			n = (size < 0) ? n + size : n + "+" + size;
			return n + " bytes)";
		}
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
	public static Commit toCommit(Git git, RevCommit before, RevCommit after) throws GitAPIException, IOException {
		final Repository repository = git.getRepository();
		AbstractTreeIterator newTreeIter = getTreeIterator(repository, after);
		List<Commit.Entry> entries = new ArrayList<>();
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
					entries.add(new Commit.Entry(treeWalk.getPathString(), new byte[0], bytes));
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
	private static Commit.Entry toEntry(Repository repository, DiffEntry e)
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
		return new Commit.Entry(path,oldBytes,newBytes);
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
}
