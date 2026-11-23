package com.portfolio.insurance.batch.collection;

import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.infrastructure.item.ExecutionContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Splits the detail lines of a result file into contiguous ranges, one per worker. Positions are item numbers
 * of the reader: item 1 is the header, details are items 2 .. detailCount + 1, the trailer is never read.
 * <p>
 * The requested grid size is ignored in favour of the job's {@code partitions} parameter; there are never more
 * partitions than lines.
 */
class DetailRangePartitioner implements Partitioner {

    static final String FROM_ITEM = "fromItem";
    static final String TO_ITEM = "toItem";

    private final long detailCount;
    private final int partitions;

    DetailRangePartitioner(long detailCount, int partitions) {
        this.detailCount = detailCount;
        this.partitions = (int) Math.max(1, Math.min(partitions, Math.max(detailCount, 1)));
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        Map<String, ExecutionContext> result = new LinkedHashMap<>();
        long size = detailCount / partitions;
        long remainder = detailCount % partitions;
        long from = 2;
        for (int i = 0; i < partitions; i++) {
            long count = size + (i < remainder ? 1 : 0);
            ExecutionContext context = new ExecutionContext();
            context.putLong(FROM_ITEM, from);
            context.putLong(TO_ITEM, from + count - 1);
            result.put("partition" + i, context);
            from += count;
        }
        return result;
    }
}
