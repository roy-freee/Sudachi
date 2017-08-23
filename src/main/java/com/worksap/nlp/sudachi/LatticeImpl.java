package com.worksap.nlp.sudachi;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.worksap.nlp.sudachi.dictionary.Grammar;

class LatticeImpl implements Lattice {

    private List<List<LatticeNodeImpl>> endLists;
    private int size;
    private LatticeNodeImpl eosNode;

    private Grammar grammar;

    LatticeImpl(int size, Grammar grammar) {
        this.size = size;
        this.grammar = grammar;

        LatticeNodeImpl bosNode = new LatticeNodeImpl();
        short[] bosParams = grammar.getBOSParameter();
        bosNode.setParameter(bosParams[0], bosParams[1], bosParams[2]);
        bosNode.isConnectedToBOS = true;

        eosNode = new LatticeNodeImpl();
        short[] eosParams = grammar.getEOSParameter();
        eosNode.setParameter(eosParams[0], eosParams[1], eosParams[2]);
        eosNode.begin = eosNode.end = size;

        endLists = new ArrayList<List<LatticeNodeImpl>>(size + 1);
        endLists.add(Collections.singletonList(bosNode));
        for (int i = 1; i < size + 1; i++) {
            endLists.add(new ArrayList<LatticeNodeImpl>());
        }
    }

    @Override
    public List<LatticeNodeImpl> getNodesWithEnd(int end) {
        return endLists.get(end);
    }

    @Override
    public List<LatticeNodeImpl> getNodes(int begin, int end) {
        return endLists.get(end).stream()
            .filter(n -> ((LatticeNodeImpl)n).begin == begin)
            .collect(Collectors.toList());
    }

    @Override
    public void insert(int begin, int end, LatticeNode node) {
        LatticeNodeImpl n = (LatticeNodeImpl)node;
        endLists.get(end).add(n);
        n.begin = begin;
        n.end = end;

        connectNode(n);
    }

    @Override
    public void remove(int begin, int end, LatticeNode node) {
        endLists.get(end).remove(node);
    }

    @Override
    public LatticeNode createNode() {
        return new LatticeNodeImpl();
    }

    boolean hasPreviousNode(int index) {
        return !endLists.get(index).isEmpty();
    }

    void connectNode(LatticeNodeImpl rNode) {
        int begin = rNode.begin;
        rNode.totalCost = Integer.MAX_VALUE;
        for (LatticeNodeImpl lNode : endLists.get(begin)) {
            if (!lNode.isConnectedToBOS) {
                continue;
            }
            short connectCost
                = grammar.getConnectCost(lNode.rightId, rNode.leftId);
            if (connectCost == Grammar.INHIBITED_CONNECTION) {
                continue; // this connection is not allowed
            }
            int cost = lNode.totalCost + connectCost;
            if (cost < rNode.totalCost) {
                rNode.totalCost = cost;
                rNode.bestPreviousNode = lNode;
            }
        }
        rNode.isConnectedToBOS = !(rNode.bestPreviousNode == null);
        rNode.totalCost += rNode.cost;
    }

    List<LatticeNode> getBestPath() {
        connectNode(eosNode);
        if (!eosNode.isConnectedToBOS) { // EOS node
            throw new IllegalStateException("EOS isn't connected to BOS");
        }
        ArrayList<LatticeNode> result = new ArrayList<>();
        for (LatticeNodeImpl node = eosNode;
             node != endLists.get(0).get(0);
             node = node.bestPreviousNode) {
            result.add(node);
        }
        Collections.reverse(result);
        return result;
    }

    void dump(PrintStream output) {
        int index = 0;
        for (int i = size; i >= 0; i--) {
            for (LatticeNodeImpl rNode : endLists.get(i)) {
                output.print(String.format("%d: %s: ", index,
                                           rNode.toString()));
                index++;
                for (LatticeNodeImpl lNode : endLists.get(rNode.begin)) {
                    int cost = lNode.totalCost
                        + grammar.getConnectCost(lNode.rightId, rNode.leftId);
                    output.print(String.format("%d ", cost));
                }
                output.println();
            }
        }
    }
}
