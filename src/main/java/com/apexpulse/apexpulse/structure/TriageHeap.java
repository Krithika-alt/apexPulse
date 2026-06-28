package com.apexpulse.apexpulse.structure;

import com.apexpulse.apexpulse.model.Patient;
import java.util.ArrayList;
import java.util.List;

public class TriageHeap {

    private final ArrayList<Patient> heap = new ArrayList<>();

    public void insert(Patient p) {
        heap.add(p);
        percolateUp(heap.size() - 1);
    }

    public Patient peekMax() {
        if (heap.isEmpty()) return null;
        return heap.get(0);
    }

    public Patient extractMax() {
        if (heap.isEmpty()) return null;
        Patient max = heap.get(0);
        Patient last = heap.remove(heap.size() - 1);
        if (!heap.isEmpty()) {
            heap.set(0, last);
            percolateDown(0);
        }
        return max;
    }

    public int size() { return heap.size(); }

    public List<Patient> drainSorted() {
        // Return a priority-sorted snapshot without destroying the heap
        ArrayList<Patient> backup = new ArrayList<>(this.heap);
        List<Patient> sortedList = new ArrayList<>();

        while (!this.heap.isEmpty()) {
            sortedList.add(this.extractMax());
        }

        this.heap.addAll(backup);
        return sortedList;
    }

    private void percolateUp(int i) {
        while (i > 0) {
            int parent = (i - 1) / 2;
            if (heap.get(i).getPriorityScore() > heap.get(parent).getPriorityScore()) {
                swap(i, parent);
                i = parent;
            } else break;
        }
    }

    private void percolateDown(int i) {
        int n = heap.size();
        while (true) {
            int largest = i;
            int left  = 2 * i + 1;
            int right = 2 * i + 2;
            if (left  < n && heap.get(left).getPriorityScore()  > heap.get(largest).getPriorityScore()) largest = left;
            if (right < n && heap.get(right).getPriorityScore() > heap.get(largest).getPriorityScore()) largest = right;
            if (largest == i) break;
            swap(i, largest);
            i = largest;
        }
    }

    private void swap(int a, int b) {
        Patient tmp = heap.get(a);
        heap.set(a, heap.get(b));
        heap.set(b, tmp);
    }

    public void rebuildHeap() {
        // Run percolateDown from the middle of the array back to the top to fix any shifting priorities
        int n = heap.size();
        for (int i = (n / 2) - 1; i >= 0; i--) {
            percolateDown(i);
        }
    }
}
