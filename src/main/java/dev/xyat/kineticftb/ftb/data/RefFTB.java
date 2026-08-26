package dev.xyat.kineticftb.ftb.data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public record RefFTB(long id, String code, String title, String chapter, String source, String searchText) {
    public static List<RefFTB> dedupe(List<RefFTB> input) {
        ArrayList<RefFTB> out = new ArrayList<>();
        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        for (RefFTB ref : input) {
            if (ref != null && seen.add(ref.id())) {
                out.add(ref);
            }
        }
        return out;
    }
}
