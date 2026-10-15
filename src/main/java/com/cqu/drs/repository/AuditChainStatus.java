package com.cqu.drs.repository;

/**
 * The result of checking that the audit trail has not been altered.
 *
 * @param supported        whether this store keeps a tamper-evident chain at
 *                         all. The JSON store inherited from DRS-Initial does
 *                         not, and says so rather than reporting a clean
 *                         result it has no means of establishing
 * @param intact           whether every entry's hash matches what it should be
 * @param entriesChecked   how many entries were read
 * @param brokenAtSequence the sequence number of the first entry that failed,
 *                         or -1 when nothing failed
 * @param detail           what was found, in words, for the person reading it
 */
public record AuditChainStatus(boolean supported,
                               boolean intact,
                               int entriesChecked,
                               long brokenAtSequence,
                               String detail) {

    /**
     * A chain that was checked and found whole.
     *
     * @param entriesChecked how many entries were read
     * @return the status
     */
    public static AuditChainStatus intact(int entriesChecked) {
        return new AuditChainStatus(true, true, entriesChecked, -1,
                entriesChecked + " audit entries were checked and the chain is unbroken.");
    }

    /**
     * A chain that does not add up.
     *
     * @param entriesChecked   how many entries were read before the break
     * @param brokenAtSequence where it broke
     * @param detail           what was wrong
     * @return the status
     */
    public static AuditChainStatus broken(int entriesChecked, long brokenAtSequence,
                                          String detail) {
        return new AuditChainStatus(true, false, entriesChecked, brokenAtSequence, detail);
    }

    /**
     * A store that keeps no chain.
     *
     * Reporting this rather than "intact" matters. A store with no chain
     * cannot tell whether it has been altered, and saying so is the truth;
     * returning a clean result would be a claim it has no way to support.
     *
     * @param storeDescription which store this is
     * @return the status
     */
    public static AuditChainStatus notSupported(String storeDescription) {
        return new AuditChainStatus(false, false, 0, -1,
                storeDescription + " keeps no hash chain, so whether its audit "
                        + "entries have been altered cannot be determined.");
    }
}
