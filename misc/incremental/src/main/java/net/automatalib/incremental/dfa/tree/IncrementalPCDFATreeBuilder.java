/* Copyright (C) 2014 TU Dortmund
 * This file is part of AutomataLib, http://www.automatalib.net/.
 * 
 * AutomataLib is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License version 3.0 as published by the Free Software Foundation.
 * 
 * AutomataLib is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with AutomataLib; if not, see
 * http://www.gnu.de/documents/lgpl.en.html.
 */
package net.automatalib.incremental.dfa.tree;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;

import net.automatalib.automata.fsa.DFA;
import net.automatalib.commons.util.mappings.MutableMapping;
import net.automatalib.incremental.ConflictException;
import net.automatalib.incremental.dfa.Acceptance;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
import net.automatalib.words.WordBuilder;

public class IncrementalPCDFATreeBuilder<I> extends
		IncrementalDFATreeBuilder<I> {
	
	public class TransitionSystemView extends IncrementalDFATreeBuilder<I>.TransitionSystemView {
		@Override
		public Node<I> getTransition(Node<I> state, I input) {
			if(state.getAcceptance() == Acceptance.FALSE) {
				return state;
			}
			return super.getTransition(state, input);
		}
	}
	
	
	private Node<I> sink = null;
	
	public IncrementalPCDFATreeBuilder(Alphabet<I> alphabet) {
		super(alphabet);
	}
	
	
	@Override
	public TransitionSystemView asTransitionSystem() {
		return new TransitionSystemView();
	}
	
	@Override
	public Acceptance lookup(Word<I> inputWord) {
		Node<I> curr = root;
		
		for(I sym : inputWord) {
			if(curr.getAcceptance() == Acceptance.FALSE) {
				return Acceptance.FALSE;
			}
			
			int symIdx = inputAlphabet.getSymbolIndex(sym);
			Node<I> succ = curr.getChild(symIdx);
			if(succ == null) {
				return Acceptance.DONT_KNOW;
			}
			curr = succ;
		}
		return curr.getAcceptance();
	}
	
	@Override
	public void insert(Word<I> word, boolean acceptance) throws ConflictException {
		if(acceptance) {
			insertTrue(word);
		}
		else {
			insertFalse(word);
		}
	}
	
	private void insertTrue(Word<I> word) throws ConflictException {
		Node<I> curr = root;
		
		int idx = 0;
		for(I sym : word) {
			if(curr.getAcceptance() == Acceptance.FALSE) {
				throw new ConflictException("Conflicting acceptance values for word " + word.prefix(idx) + ": found FALSE, expected DONT_KNOW or TRUE");
			}
			curr.setAcceptance(Acceptance.TRUE);
			int symIdx = inputAlphabet.getSymbolIndex(sym);
			Node<I> succ = curr.getChild(symIdx);
			if(succ == null) {
				succ = new Node<I>(Acceptance.TRUE);
				curr.setChild(symIdx, alphabetSize, succ);
			}
			curr = succ;
			idx++;
		}
		if(curr.getAcceptance() == Acceptance.FALSE) {
			throw new ConflictException("Conflicting acceptance values for word " + word + ": found FALSE, expected DONT_KNOW or TRUE");
		}
		curr.setAcceptance(Acceptance.TRUE);
	}
	
	private void insertFalse(Word<I> word) throws ConflictException {
		Node<I> curr = root;
		Node<I> prev = null;
		int lastSymIdx = -1;
		
		for(I sym : word) {
			if(curr.getAcceptance() == Acceptance.FALSE) {
				return; // done!
			}
			int symIdx = inputAlphabet.getSymbolIndex(sym);
			Node<I> succ = curr.getChild(symIdx);
			if(succ == null) {
				succ = new Node<I>(Acceptance.DONT_KNOW);
				curr.setChild(symIdx, alphabetSize, succ);
			}
			prev = curr;
			curr = succ;
			lastSymIdx = symIdx;
		}
		
		if(curr.getAcceptance() == Acceptance.TRUE) {
			throw new ConflictException("Conflicting acceptance values for word " + word + ": found TRUE, expected DONT_KNOW or FALSE");
		}
		
		
		// Note that we do not need to look deeper into the tree, because
		// if any of the successor of curr would have an acceptance value
		// of true, also curr would
		if(prev == null) {
			assert curr == root;
			root.makeSink();
		}
		else {
			Node<I> sink = getSink();
			prev.setChild(lastSymIdx, alphabetSize, sink);
		}
	}
	
	@Override
	protected <S> Word<I> doFindSeparatingWord(final DFA<S,I> target, Collection<? extends I> inputs, boolean omitUndefined) {
		
		S automatonInit = target.getInitialState();
		Acceptance rootAcc = root.getAcceptance();
		if(rootAcc.conflicts(target.isAccepting(automatonInit))) {
			return Word.epsilon();
		}
		if(rootAcc == Acceptance.FALSE) {
			return findLive(target, automatonInit, inputs, target.<Boolean>createStaticStateMapping());
		}
		
		Deque<Record<S,I>> dfsStack = new ArrayDeque<>();
		dfsStack.push(new Record<>(automatonInit, root, null, inputs.iterator()));
		
		MutableMapping<S,Boolean> deadStates = null;
		
		while(!dfsStack.isEmpty()) {
			Record<S,I> rec = dfsStack.peek();
			if(!rec.inputIt.hasNext()) {
				dfsStack.pop();
				continue;
			}
			I input = rec.inputIt.next();
			int inputIdx = inputAlphabet.getSymbolIndex(input);
			
			Node<I> succ = rec.treeNode.getChild(inputIdx);
			if(succ == null) {
				continue;
			}
			
			Acceptance acc = succ.getAcceptance();
			
			S automatonSucc = (rec.automatonState == null) ? null : target.getTransition(rec.automatonState, input);
			if(automatonSucc == null && (omitUndefined || acc == Acceptance.FALSE)) {
				continue;
			}
			
			boolean succAcc = (automatonSucc == null) ? false : target.isAccepting(automatonSucc);
			
			Word<I> liveSuffix = null;
			if(acc == Acceptance.FALSE) {
				if(deadStates == null) {
					deadStates = target.createStaticStateMapping();
				}
				liveSuffix = findLive(target, automatonSucc, inputs, deadStates);
			}
			
			if(acc.conflicts(succAcc) || (liveSuffix != null)) {
				WordBuilder<I> wb = new WordBuilder<>(dfsStack.size());
				wb.append(input);
				
				dfsStack.pop();
				do {
					wb.append(rec.incomingInput);
					rec = dfsStack.pop();
				} while(!dfsStack.isEmpty());
				wb.reverse();
				if(liveSuffix != null) {
					wb.append(liveSuffix);
				}
				return wb.toWord();
			}
						
			dfsStack.push(new Record<>(automatonSucc, succ, input, inputs.iterator()));
		}
		
		return null;
	}
	
	private static final class FindLiveRecord<S,I> {
		public final S state;
		public final I incomingInput;
		public final Iterator<? extends I> inputIt;
		
		public FindLiveRecord(S state, I incomingInput, Iterator<? extends I> inputIt) {
			this.state = state;
			this.incomingInput = incomingInput;
			this.inputIt = inputIt;
		}
	}
	
	private static <S,I> Word<I> findLive(DFA<S,I> dfa, S state, Collection<? extends I> inputs, MutableMapping<S,Boolean> deadStates) {
		Deque<FindLiveRecord<S,I>> dfsStack = new ArrayDeque<>();
		
		if(dfa.isAccepting(state)) {
			return Word.epsilon();
		}
		
		Boolean dead = deadStates.get(state);
		if(dead != null && dead) {
			return null;
		}
		deadStates.put(state, true);
		
		dfsStack.push(new FindLiveRecord<>(state, null, inputs.iterator()));
		
		while(!dfsStack.isEmpty()) {
			FindLiveRecord<S,I> rec = dfsStack.peek();
			if(!rec.inputIt.hasNext()) {
				dfsStack.pop();
				continue;
			}
			I input = rec.inputIt.next();
			
			S succ = dfa.getTransition(rec.state, input);
			if(succ == null) {
				continue;
			}
			if(dfa.isAccepting(succ)) {
				WordBuilder<I> wb = new WordBuilder<>(dfsStack.size());
				wb.append(input);
				
				dfsStack.pop();
				while(!dfsStack.isEmpty()) {
					wb.append(rec.incomingInput);
					rec = dfsStack.pop();
				}
				return wb.reverse().toWord();
			}
			
			dead = deadStates.get(succ);
			if(dead == null) {
				dfsStack.push(new FindLiveRecord<>(succ, input, inputs.iterator()));
				deadStates.put(succ, true);
			}
			else {
				assert(dead);
			}
		}
		
		return null;
	}
	
	public Node<I> getSink() {
		if(sink == null) {
			sink = new Node<>(Acceptance.FALSE);
		}
		return sink;
	}
	
	
}
