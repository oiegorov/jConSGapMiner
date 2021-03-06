/*
	This file is part of jConSGapMiner - see https://github.com/slide-lig/jConSGapMiner
	
	Copyright 2014 Vincent Leroy, Université Joseph Fourier and CNRS

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

	 http://www.apache.org/licenses/LICENSE-2.0
	 
	or see the LICENSE.txt file joined with this program.

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
 */

package fr.liglab.consgap.collector;

import gnu.trove.iterator.TIntIterator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class BatchFilteringResultsCollector extends ResultsCollector {

	private List<int[]> collectedSeq;
	private TreeNode filteringTree;
	private int nbCollected;
	private int collectSinceBatch;
	private final int interBatchDelay;
	private boolean batchInProgress;

	// in this class, we build tree and check from the last item in the sequence
	// to detect collision in prefix first as it gives more pruning
	public BatchFilteringResultsCollector(int interBatchDelay) {
		this.collectedSeq = new ArrayList<>();
		this.nbCollected = 0;
		this.filteringTree = null;
		this.collectSinceBatch = 0;
		this.interBatchDelay = interBatchDelay;
		this.batchInProgress = false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see fr.liglab.consgap.internals.ResultsCollector#collect(int[], int)
	 */
	@Override
	public EmergingStatus collect(int[] sequence, int expansionItem) {
		int[] fullSeq = new int[sequence.length + 1];
		System.arraycopy(sequence, 0, fullSeq, 1, sequence.length);
		fullSeq[0] = expansionItem;
		TreeNode root = this.filteringTree;
		if (root != null) {
			int lastPos = recursiveSubsetCheck(root, fullSeq, fullSeq.length - 1);
			if (lastPos >= 0) {
				if (lastPos == 0) {
					return EmergingStatus.EMERGING_WITH_EXPANSION;
				} else {
					return EmergingStatus.EMERGING_WITHOUT_EXPANSION;
				}
			}
		}
		List<int[]> batchSeq = null;
		synchronized (this) {
			collectedSeq.add(fullSeq);
			this.nbCollected++;
			this.collectSinceBatch++;
			if (this.collectSinceBatch >= this.interBatchDelay) {
				if (!this.batchInProgress) {
					this.batchInProgress = true;
					batchSeq = this.collectedSeq;
					this.collectedSeq = new ArrayList<int[]>();
					this.collectSinceBatch = 0;
				}
			}
		}
		if (batchSeq != null) {
			TreeNode newRoot = new TreeNode();
			List<int[]> filtered = getNonRedundant(newRoot, null, batchSeq);
			this.filteringTree = newRoot;
			synchronized (this) {
				this.collectedSeq.addAll(filtered);
				this.batchInProgress = false;
			}
		}
		return EmergingStatus.NEW_EMERGING;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see fr.liglab.consgap.internals.ResultsCollector#getNbCollected()
	 */
	@Override
	public int getNbCollected() {
		return this.nbCollected++;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see fr.liglab.consgap.internals.ResultsCollector#getNonRedundant()
	 */
	@Override
	public List<int[]> getNonRedundant() {
		List<int[]> filtered = getNonRedundant(new TreeNode(), this.rebasing, this.collectedSeq);
		TIntIterator iter = this.emergingItems.iterator();
		while (iter.hasNext()) {
			filtered.add(new int[] { iter.next() });
		}
		return filtered;
	}

	private static List<int[]> getNonRedundant(TreeNode rootNode, int[] rebasing, List<int[]> sequences) {
		// sort sequences by size and then lexico
		Collections.sort(sequences, new Comparator<int[]>() {

			@Override
			public int compare(int[] o1, int[] o2) {
				int diffSize = o1.length - o2.length;
				if (diffSize != 0) {
					return diffSize;
				} else {
					for (int i = 0; i < o1.length; i++) {
						if (o1[i] != o2[i]) {
							return o1[i] - o2[i];
						}
					}
					return 0;
				}
			}
		});
		List<int[]> nonRedundant = new ArrayList<>();
		for (int[] seq : sequences) {
			if (recursiveSubsetCheck(rootNode, seq, seq.length - 1) < 0) {
				if (rebasing != null) {
					int[] rebasedSeq = new int[seq.length];
					for (int i = 0; i < rebasedSeq.length; i++) {
						rebasedSeq[i] = rebasing[seq[i]];
					}
					nonRedundant.add(rebasedSeq);
				} else {
					nonRedundant.add(seq);
				}
				// insert in tree
				insertIntoTree(rootNode, seq);
			}
		}
		return nonRedundant;
	}

	// checks from left to right
	static private int recursiveSubsetCheck(TreeNode currentNode, int[] seq, int from) {
		for (int i = from; i >= 0; i--) {
			TreeNode nextNode = currentNode.get(seq[i]);
			if (nextNode != null) {
				// if it's a leaf, we're done
				if (nextNode.isEmpty()) {
					return i;
				} else {
					int subSetCheck = recursiveSubsetCheck(nextNode, seq, i - 1);
					if (subSetCheck >= 0) {
						return subSetCheck;
					}
				}
			}
		}
		return -1;
	}

	static private void insertIntoTree(TreeNode rootNode, int[] seq) {
		TreeNode currentNode = rootNode;
		for (int i = seq.length - 1; i >= 0; i--) {
			int item = seq[i];
			TreeNode nextNode = currentNode.get(item);
			if (nextNode == null) {
				nextNode = new TreeNode();
				currentNode.put(item, nextNode);
			}
			currentNode = nextNode;
		}
	}
}
