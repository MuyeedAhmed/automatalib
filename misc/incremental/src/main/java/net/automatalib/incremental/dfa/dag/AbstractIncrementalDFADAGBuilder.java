/* Copyright (C) 2013 TU Dortmund
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
package net.automatalib.incremental.dfa.dag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.automatalib.automata.concepts.StateIDs;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.commons.util.UnionFind;
import net.automatalib.graphs.dot.DelegateDOTHelper;
import net.automatalib.graphs.dot.GraphDOTHelper;
import net.automatalib.incremental.dfa.AbstractIncrementalDFABuilder;
import net.automatalib.incremental.dfa.Acceptance;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
import net.automatalib.words.WordBuilder;

public abstract class AbstractIncrementalDFADAGBuilder<I>
		extends AbstractIncrementalDFABuilder<I> {
	
	@ParametersAreNonnullByDefault
	public class GraphView extends AbstractGraphView<I,State,EdgeRecord> {
		@Override
		public Collection<State> getNodes() {
			if(sink == null) {
				return Collections.unmodifiableCollection(register.values());
			}
			List<State> result = new ArrayList<>(register.size() + 1);
			result.addAll(register.values());
			result.add(sink);
			return result;
		}
		@Override
		public int size() {
			return register.size() + ((sink == null) ? 0 : 1);
		}
		@Override
		public Collection<EdgeRecord> getOutgoingEdges(State node) {
			if(node.isSink()) {
				return Collections.emptySet();
			}
			StateSignature sig = node.getSignature();
			List<EdgeRecord> result = new ArrayList<>();
			for(int i = 0; i < alphabetSize; i++) {
				if(sig.successors[i] != null) {
					result.add(new EdgeRecord(node, i));
				}
			}
			return result;
		}
		@Override
		@Nonnull
		public State getTarget(EdgeRecord edge) {
			int idx = edge.transIdx;
			return edge.source.getSuccessor(idx);
		}
		@Override
		@Nonnull
		public Acceptance getAcceptance(State node) {
			return node.getAcceptance();
		}
		@Override
		@Nullable
		public I getInputSymbol(EdgeRecord edge) {
			return inputAlphabet.getSymbol(edge.transIdx);
		}
		
		@Override
		@Nonnull
		public GraphDOTHelper<State, EdgeRecord> getGraphDOTHelper() {
			return new DelegateDOTHelper<State,EdgeRecord>(super.getGraphDOTHelper()) {
				private int id = 0;
				@Override
				public boolean getNodeProperties(State node,
						Map<String, String> properties) {
					if(!super.getNodeProperties(node, properties)) {
						return false;
					}
					properties.put(NodeAttrs.LABEL, "n" + (id++));
					if(node.isConfluence()) {
						String shape = (node.getAcceptance() == Acceptance.TRUE) ? NodeShapes.DOUBLEOCTAGON : NodeShapes.OCTAGON;
						properties.put(NodeAttrs.SHAPE, shape);
					}
					return true;
				}
			};
		}
		@Override
		@Nonnull
		public State getInitialNode() {
			return init;
		}
	}
	
	public class TransitionSystemView extends AbstractTransitionSystemView<State, I, State> {
		@Override
		public State getSuccessor(State transition) {
			return transition;
		}
		@Override
		public State getTransition(State state, I input) {
			if(state == sink) {
				return state;
			}
			int idx = inputAlphabet.getSymbolIndex(input);
			return state.getSuccessor(idx);
		}
		@Override
		public State getInitialState() {
			return init;
		}
		@Override
		@Nonnull
		public Acceptance getAcceptance(State state) {
			return state.getAcceptance();
		}
	}

	protected final Map<StateSignature, State> register = new HashMap<>();

	protected final State init;
	protected State sink;
	
	public AbstractIncrementalDFADAGBuilder(Alphabet<I> inputAlphabet) {
		super(inputAlphabet);
		StateSignature sig = new StateSignature(alphabetSize, Acceptance.DONT_KNOW);
		this.init = new State(sig);
		register.put(null, init);
	}
	
	
	@Override
	public Word<I> findSeparatingWord(DFA<?, I> target, Collection<? extends I> inputs, boolean omitUndefined) {
		return doFindSeparatingWord(target, inputs, omitUndefined);
	}
	
	
	
	/*
	 * Equivalence test
	 */
	
	private static int getStateId(State s, Map<State,Integer> idMap) {
		Integer id = idMap.get(s);
		if(id != null)
			return id.intValue();
		idMap.put(s, id = idMap.size());
		return id.intValue();
	}
	
	private static final class Record<S,I> {
		public final State state1;
		public final S state2;
		public final I reachedVia;
		public final Record<S,I> reachedFrom;
		public final int depth;
		
		public Record(State state1, S state2) {
			this.state1 = state1;
			this.state2 = state2;
			this.reachedVia = null;
			this.reachedFrom = null;
			this.depth = 0;
		}
		
		public Record(State state1, S state2, I reachedVia, Record<S,I> reachedFrom) {
			this.state1 = state1;
			this.state2 = state2;
			this.reachedVia = reachedVia;
			this.reachedFrom = reachedFrom;
			this.depth = reachedFrom.depth + 1;
		}
	}
	
	private <S> Word<I> doFindSeparatingWord(DFA<S,I> target, Collection<? extends I> inputs, boolean omitUndefined) {
		int thisStates = register.size();
		Map<State,Integer> stateIds = new HashMap<>();
		if(sink != null) {
			stateIds.put(sink, 0);
			thisStates++;
		}
		int targetStates = target.size();
		if(!omitUndefined)
			targetStates++;
		
		UnionFind uf = new UnionFind(thisStates + targetStates);

		State init1 = init;
		S init2 = target.getInitialState();
		
		if(init2 == null && omitUndefined)
			return null;
		
		boolean acc = target.isAccepting(init2);
		if(init1.getAcceptance().conflicts(acc))
			return Word.epsilon();
		
		StateIDs<S> tgtIds = target.stateIDs();
		int id1 = getStateId(init1, stateIds);
		int id2 = ((init2 != null) ? tgtIds.getStateId(init2) : (targetStates - 1)) + thisStates;
		
		uf.link(id1, id2);
		
		Queue<Record<S,I>> queue = new ArrayDeque<>();
		
		queue.add(new Record<S,I>(init1, init2));
		
		I lastSym = null;
		
		Record<S,I> current;
		
explore:while((current = queue.poll()) != null) {
			State state1 = current.state1;
			S state2 = current.state2;
			
			for(I sym : inputs) {
				S succ2 = (state2 != null) ? target.getSuccessor(state2, sym) : null;
				if(succ2 == null && omitUndefined)
					continue;
				
				int idx = inputAlphabet.getSymbolIndex(sym);
				State succ1 = (state1 != sink) ? state1.getSuccessor(idx) : sink;
				
				if(succ1 == null)
					continue;
				
				id1 = getStateId(succ1, stateIds);
				id2 = ((succ2 != null) ? tgtIds.getStateId(succ2) : (targetStates-1)) + thisStates;
				
				int r1 = uf.find(id1), r2 = uf.find(id2);
				
				if(r1 == r2)
					continue;
				
				if(succ1 == sink) {
					if(succ2 == null)
						continue;
					if(target.isAccepting(succ2)) {
						lastSym = sym;
						break explore;
					}
				}
				else {
					boolean succ2acc = (succ2 != null) ? target.isAccepting(succ2) : false;
					if(succ1.getAcceptance().conflicts(succ2acc)) {
						lastSym = sym;
						break explore;
					}
				}
				
				uf.link(r1, r2);
				
				queue.add(new Record<>(succ1, succ2, sym, current));
			}
        }
		
		if(current == null)
			return null;
		
		int ceLength = current.depth;
		if(lastSym != null)
			ceLength++;
		
		WordBuilder<I> wb = new WordBuilder<I>(null, ceLength);
		
		int index = ceLength;
		
		if(lastSym != null)
			wb.setSymbol(--index, lastSym);
		
		while(current.reachedFrom != null) {
			wb.setSymbol(--index, current.reachedVia);
			current = current.reachedFrom;
		}
		
		return wb.toWord();
	}
	
	
	
	protected abstract State getState(Word<I> word);
	
	/**
	 * Returns (and possibly creates) the canonical state for the given signature.
	 * @param sig the signature
	 * @return the canonical state for the given signature
	 */
	protected State replaceOrRegister(StateSignature sig) {
		State state = register.get(sig);
		if(state != null)
			return state;
		
		register.put(sig, state = new State(sig));
		for(int i = 0; i < sig.successors.length; i++) {
			State succ = sig.successors[i];
			if(succ != null)
				succ.increaseIncoming();
		}
		return state;
	}
	
	/**
	 * Returns the canonical state for the given state's signature, or registers the
	 * state as canonical if no state with that signature exists.
	 * @param state the state
	 * @return the canonical state for the given state's signature
	 */
	protected State replaceOrRegister(State state) {
		StateSignature sig = state.getSignature();
		State other = register.get(sig);
		if(other != null) {
			if(state != other) {
				for(int i = 0; i < sig.successors.length; i++) {
					State succ = sig.successors[i];
					if(succ != null)
						succ.decreaseIncoming();
				}
			}
			return other;
		}
		
		register.put(sig, state);
		return state;
	}
	
	protected void updateInitSignature(Acceptance acc) {
		StateSignature sig = init.getSignature();
		sig.acceptance = acc;
	}
	

	/**
	 * Updates the signature for a given state.
	 * @param state the state
	 * @param acc the new acceptance value
	 * @return the canonical state for the updated signature
	 */
	protected State updateSignature(State state, Acceptance acc) {
		assert (state != init);
		StateSignature sig = state.getSignature();
		if(sig.acceptance == acc)
			return state;
		register.remove(sig);
		sig.acceptance = acc;
		sig.updateHashCode();
		return replaceOrRegister(state);
	}
	
	protected void updateInitSignature(int idx, State succ) {
		StateSignature sig = init.getSignature();
		State oldSucc = sig.successors[idx];
		if(oldSucc == succ)
			return;
		if(oldSucc != null)
			oldSucc.decreaseIncoming();
		sig.successors[idx] = succ;
		succ.increaseIncoming();
	}
	
	protected void updateInitSignature(Acceptance acc, int idx, State succ) {
		StateSignature sig = init.getSignature();
		State oldSucc = sig.successors[idx];
		Acceptance oldAcc = sig.acceptance;
		if(oldSucc == succ && oldAcc == acc)
			return;
		if(oldSucc != null)
			oldSucc.decreaseIncoming();
		sig.successors[idx] = succ;
		succ.increaseIncoming();
		sig.acceptance = acc;
	}
	
	/**
	 * Updates the signature for a given state.
	 * @param state the state
	 * @param idx the index of the transition to change
	 * @param succ the new successor for the above index
	 * @return the canonical state for the updated signature
	 */
	protected State updateSignature(State state, int idx, State succ) {
		assert (state != init);
		
		StateSignature sig = state.getSignature();
		if(sig.successors[idx] == succ)
			return state;
		register.remove(sig);
		if(sig.successors[idx] != null)
			sig.successors[idx].decreaseIncoming();
		
		sig.successors[idx] = succ;
		succ.increaseIncoming();
		sig.updateHashCode();
		return replaceOrRegister(state);
	}
	
	protected State updateSignature(State state, Acceptance acc, int idx, State succ) {
		assert (state != init);
		
		StateSignature sig = state.getSignature();
		if(sig.successors[idx] == succ && sig.acceptance == acc)
			return state;
		register.remove(sig);
		sig.successors[idx] = succ;
		sig.acceptance = acc;
		return replaceOrRegister(state);
	}
	
	/**
	 * Clones a state, changing the signature.
	 * @param other the state to clone
	 * @param acc the new acceptance value
	 * @return the canonical state for the derived signature
	 */
	protected State clone(State other, Acceptance acc) {
		assert (other != init);
		
		StateSignature sig = other.getSignature();
		if(sig.acceptance == acc)
			return other;
		sig = sig.duplicate();
		sig.acceptance = acc;
		sig.updateHashCode();
		return replaceOrRegister(sig);
	}
	
	protected State hiddenClone(State other) {
		assert (other != init);
		
		StateSignature sig = other.getSignature().duplicate();
		for(int i = 0; i < alphabetSize; i++) {
			State succ = sig.successors[i];
			if(succ != null) {
				succ.increaseIncoming();
			}
		}
		return new State(sig);
	}
	
	protected void hide(State state) {
		assert (state != init);
		
		StateSignature sig = state.getSignature();
		register.remove(sig);
	}
	
	protected State unhide(State state, Acceptance acc, int idx, State succ) {
		assert (state != init);
		
		StateSignature sig = state.getSignature();
		sig.acceptance = acc;
		State prevSucc = sig.successors[idx];
		if(prevSucc != null) {
			prevSucc.decreaseIncoming();
		}
		sig.successors[idx] = succ;
		if(succ != null) {
			succ.increaseIncoming();
		}
		sig.updateHashCode();
		
		return replaceOrRegister(state);
	}
	
	protected State unhide(State state, int idx, State succ) {
		assert (state != init);
		
		StateSignature sig = state.getSignature();
		State prevSucc = sig.successors[idx];
		if(prevSucc != null) {
			prevSucc.decreaseIncoming();
		}
		sig.successors[idx] = succ;
		if(succ != null) {
			succ.increaseIncoming();
		}
		sig.updateHashCode();
		
		return replaceOrRegister(state);
	}
	
	/**
	 * Clones a state, changing the signature.
	 * @param other the state to clone
	 * @param idx the index of the transition to change
	 * @param succ the new successor state
	 * @return the canonical state for the derived signature
	 */
	protected State clone(State other, int idx, State succ) {
		assert (other != init);
		
		StateSignature sig = other.getSignature();
		if(sig.successors[idx] == succ)
			return other;
		sig = sig.duplicate();
		sig.successors[idx] = succ;
		sig.updateHashCode();
		return replaceOrRegister(sig);
	}
	
	protected State clone(State other, Acceptance acc, int idx, State succ) {
		assert (other != init);
		
		StateSignature sig = other.getSignature();
		if(sig.successors[idx] == succ && sig.acceptance == acc)
			return other;
		sig = sig.duplicate();
		sig.successors[idx] = succ;
		sig.acceptance = acc;
		return replaceOrRegister(sig);
	}
	
	
	@Override
	public GraphView asGraph() {
		return new GraphView();
	}
	
	@Override
	public TransitionSystemView asTransitionSystem() {
		return new TransitionSystemView();
	}

}
