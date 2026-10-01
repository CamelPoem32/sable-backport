package dev.ryanhcode.sable.command;

/** Read-only acceptance of one M37 fixture run; Create still performs every transfer. */
public final class M37FunnelRunAcceptance {
    private M37FunnelRunAcceptance() {
    }

    public static Result evaluate(final boolean entityRun, final boolean extractingRun,
                                  final boolean actorObserved, final boolean blockTargetResolved,
                                  final boolean entityPresentBefore, final boolean stationaryItemSpawnObserved,
                                  final boolean itemExistedBeforeNativeQuery,
                                  final boolean movingExtractObserved,
                                  final boolean movingInsertObserved, final int sourceBefore,
                                  final int sourceAfter, final int destinationBefore,
                                  final int destinationAfter, final boolean hiddenPlotInteraction) {
        final boolean nativeTransfer = extractingRun ? movingExtractObserved : movingInsertObserved;
        final boolean targetResolved = entityRun ? entityPresentBefore && movingInsertObserved
                : extractingRun ? blockTargetResolved
                        : stationaryItemSpawnObserved && itemExistedBeforeNativeQuery;
        final boolean endToEnd = actorObserved && targetResolved && nativeTransfer
                && sourceAfter >= 0 && sourceAfter < sourceBefore
                && destinationAfter > destinationBefore && !hiddenPlotInteraction
                && (entityRun || extractingRun
                        || sourceBefore - sourceAfter == 1 && destinationAfter - destinationBefore == 1);
        return new Result(targetResolved, nativeTransfer, endToEnd);
    }

    public record Result(boolean targetResolved, boolean nativeTransfer, boolean endToEnd) {
    }
}
