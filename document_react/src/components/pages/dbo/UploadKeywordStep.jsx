import { useState, useMemo, useEffect } from "react";
import {
    Search, X, Plus, Check, ChevronRight, ChevronLeft, ChevronDown,
    Database, Sparkles, Filter, Info, AlertTriangle,
    ChevronsLeft, ChevronsRight, Lock
} from "lucide-react";

import styles from "./UploadKeywordStep.module.css";

const ITEMS_PER_PAGE = 100;

export default function UploadKeywordStep({
    allKeywords,
    selectedKeywords,
    setSelectedKeywords,
    existingKeywords,
    onBack,
    onNext,
}) {
    // Search & Filter States
    const [availableSearch, setAvailableSearch] = useState("");
    const [selectedSearch, setSelectedSearch] = useState("");

    // Debounce Search
    const [debouncedAvailableSearch, setDebouncedAvailableSearch] = useState("");
    const [debouncedSelectedSearch, setDebouncedSelectedSearch] = useState("");

    // Pagination States
    const [availablePage, setAvailablePage] = useState(1);
    const [selectedPage, setSelectedPage] = useState(1);

    // Count Threshold States
    const [showCustomThreshold, setShowCustomThreshold] = useState(false);
    const [thresholdInput, setThresholdInput] = useState({ min: 1, max: "" });
    const [activeThreshold, setActiveThreshold] = useState({ min: 1, max: Infinity });
    const [showInfo, setShowInfo] = useState(false);

    // ✅ NEW: Left Panel Expansion State
    const [expandedSections, setExpandedSections] = useState({ existing: true, new: true });

    // ✅ NEW: Right Panel Filter State
    const [selectedFilter, setSelectedFilter] = useState('ALL'); // 'ALL', 'EXISTING', 'NEW'

    // --- Auto-Select Existing Keywords & Enforce Lock ---
    useEffect(() => {
        if (!existingKeywords || !allKeywords) return;
        const autoSelectKeywords = Object.keys(allKeywords).filter(k => existingKeywords.has(k));

        if (autoSelectKeywords.length > 0) {
            setSelectedKeywords(prev => {
                const next = new Set(prev);
                let hasChanges = false;
                autoSelectKeywords.forEach(k => {
                    if (!next.has(k)) {
                        next.add(k);
                        hasChanges = true;
                    }
                });
                return hasChanges ? next : prev;
            });
        }
    }, [allKeywords, existingKeywords, setSelectedKeywords]);

    // --- Debounce Logic ---
    useEffect(() => {
        const timer = setTimeout(() => {
            setDebouncedAvailableSearch(availableSearch);
            setAvailablePage(1);
        }, 300);
        return () => clearTimeout(timer);
    }, [availableSearch]);

    useEffect(() => {
        const timer = setTimeout(() => {
            setDebouncedSelectedSearch(selectedSearch);
            setSelectedPage(1);
        }, 300);
        return () => clearTimeout(timer);
    }, [selectedSearch]);

    // --- Stats Calculation ---
    const counts = useMemo(() => Object.values(allKeywords), [allKeywords]);

    const maxCountInSet = useMemo(() => {
        if (counts.length === 0) return 0;
        return counts.reduce((max, curr) => Math.max(max, curr), 0);
    }, [counts]);

    const minCountInSet = useMemo(() => {
        if (counts.length === 0) return 0;
        return counts.reduce((min, curr) => Math.min(min, curr), Infinity);
    }, [counts]);

    useEffect(() => {
        setThresholdInput({ min: minCountInSet || 1, max: maxCountInSet || "" });
        setActiveThreshold({ min: minCountInSet || 1, max: Infinity });
    }, [minCountInSet, maxCountInSet]);

    // -------------------------------------------------------------------------
    // 1. Process AVAILABLE List (With Collapsible Logic)
    // -------------------------------------------------------------------------
    const { flatAvailableList, availableTotalCount, existingAvailableCount, newAvailableCount } = useMemo(() => {
        const existing = [];
        const newKws = [];

        Object.entries(allKeywords).forEach(([keyword, count]) => {
            if (selectedKeywords.has(keyword)) return;

            // Search & Threshold
            if (debouncedAvailableSearch && !keyword.toLowerCase().includes(debouncedAvailableSearch.toLowerCase())) return;
            if (count < activeThreshold.min || count > activeThreshold.max) return;

            const item = { keyword, count, isExisting: existingKeywords?.has(keyword), type: 'item' };
            if (item.isExisting) existing.push(item);
            else newKws.push(item);
        });

        const sorter = (a, b) => b.count - a.count || a.keyword.localeCompare(b.keyword);
        existing.sort(sorter);
        newKws.sort(sorter);

        let flatList = [];

        // Add Existing Header
        if (existing.length > 0) {
            flatList.push({
                type: 'header',
                label: `Existing Keywords (${existing.length})`,
                isExisting: true,
                expanded: expandedSections.existing
            });
            // ✅ Only add items if expanded
            if (expandedSections.existing) {
                flatList = flatList.concat(existing);
            }
        }

        // Add New Header
        if (newKws.length > 0) {
            // flatList.push({
            //     type: 'header',
            //     label: `New Keywords (${newKws.length})`,
            //     isExisting: false,
            //     expanded: expandedSections.new
            // });
            // ✅ Only add items if expanded
            // if (expandedSections.new) {
            flatList = flatList.concat(newKws);
            // }
        }

        return {
            flatAvailableList: flatList,
            availableTotalCount: flatList.length,
            existingAvailableCount: existing.length,
            newAvailableCount: newKws.length
        };
    }, [allKeywords, selectedKeywords, existingKeywords, debouncedAvailableSearch, activeThreshold, expandedSections]);

    // -------------------------------------------------------------------------
    // 2. Process SELECTED List (With Filter Logic)
    // -------------------------------------------------------------------------
    const flatSelectedList = useMemo(() => {
        const list = [];
        selectedKeywords.forEach(keyword => {
            if (debouncedSelectedSearch && !keyword.toLowerCase().includes(debouncedSelectedSearch.toLowerCase())) return;

            const count = allKeywords[keyword] || 0;
            const isExisting = existingKeywords?.has(keyword);

            // ✅ Filter logic based on Right Panel buttons
            if (selectedFilter === 'EXISTING' && !isExisting) return;
            if (selectedFilter === 'NEW' && isExisting) return;

            list.push({ keyword, count, isExisting, type: 'item' });
        });

        list.sort((a, b) => {
            if (a.isExisting !== b.isExisting) return a.isExisting ? -1 : 1;
            if (b.count !== a.count) return b.count - a.count;
            return a.keyword.localeCompare(b.keyword);
        });

        return list;
    }, [selectedKeywords, allKeywords, existingKeywords, debouncedSelectedSearch, selectedFilter]);


    // -------------------------------------------------------------------------
    // Pagination Slicing
    // -------------------------------------------------------------------------
    const availableTotalPages = Math.ceil(flatAvailableList.length / ITEMS_PER_PAGE);
    const currentAvailableItems = useMemo(() => {
        const start = (availablePage - 1) * ITEMS_PER_PAGE;
        return flatAvailableList.slice(start, start + ITEMS_PER_PAGE);
    }, [flatAvailableList, availablePage]);

    const selectedTotalPages = Math.ceil(flatSelectedList.length / ITEMS_PER_PAGE);
    const currentSelectedItems = useMemo(() => {
        const start = (selectedPage - 1) * ITEMS_PER_PAGE;
        return flatSelectedList.slice(start, start + ITEMS_PER_PAGE);
    }, [flatSelectedList, selectedPage]);


    // -------------------------------------------------------------------------
    // Handlers
    // -------------------------------------------------------------------------

    const toggleKeyword = (keyword, add) => {
        if (!add && existingKeywords?.has(keyword)) return;

        setSelectedKeywords(prev => {
            const next = new Set(prev);
            add ? next.add(keyword) : next.delete(keyword);
            return next;
        });
    };

    const toggleSection = (isExisting) => {
        setExpandedSections(prev => ({
            ...prev,
            [isExisting ? 'existing' : 'new']: !prev[isExisting ? 'existing' : 'new']
        }));
        setAvailablePage(1); // Reset pagination on collapse/expand
    };

    const selectAllFiltered = () => {
        const toAdd = flatAvailableList
            .filter(item => item.type === 'item')
            .map(item => item.keyword);
        if (toAdd.length === 0) return;
        setSelectedKeywords(prev => {
            const next = new Set(prev);
            toAdd.forEach(k => next.add(k));
            return next;
        });
    };

    const selectFilteredNew = () => {
        const toAdd = flatAvailableList
            .filter(item => item.type === 'item' && !item.isExisting)
            .map(item => item.keyword);
        if (toAdd.length === 0) return;
        setSelectedKeywords(prev => {
            const next = new Set(prev);
            toAdd.forEach(k => next.add(k));
            return next;
        });
    };

    const deselectAllFiltered = () => {
        // If searching or filtering, only remove visible unlocked items
        const toRemove = flatSelectedList
            .map(item => item.keyword)
            .filter(k => !existingKeywords?.has(k));

        setSelectedKeywords(prev => {
            const next = new Set(prev);
            toRemove.forEach(k => next.delete(k));
            return next;
        });
        setSelectedPage(1);
    };

    const handleApplyFilter = () => {
        const min = parseInt(thresholdInput.min) || 0;
        const max = thresholdInput.max === "" ? Infinity : parseInt(thresholdInput.max);
        setActiveThreshold({ min, max });
        setAvailablePage(1);
        setShowCustomThreshold(false);
    };

    const handleClearFilter = () => {
        setThresholdInput({ min: minCountInSet, max: maxCountInSet });
        setActiveThreshold({ min: 0, max: Infinity });
        setAvailablePage(1);
        setShowCustomThreshold(false);
    };

    const existingTotal = Object.keys(allKeywords).filter(k => existingKeywords?.has(k)).length;
    const newTotal = Object.keys(allKeywords).length - existingTotal;
    const isNextDisabled = existingTotal === 0 && selectedKeywords.size === 0;
    const isFilterActive = activeThreshold.min > minCountInSet || activeThreshold.max !== Infinity;

    // Helper for rendering pagination
    const renderPagination = (curr, total, setPage) => {
        if (total <= 1) return null;
        return (
            <div className={styles.pagination}>
                <button disabled={curr === 1} onClick={() => setPage(1)} className={styles.pageBtn} title="First"><ChevronsLeft size={16} /></button>
                <button disabled={curr === 1} onClick={() => setPage(curr - 1)} className={styles.pageBtn} title="Prev"><ChevronLeft size={16} /></button>
                <span className={styles.pageInfo}>Page {curr} of {total}</span>
                <button disabled={curr === total} onClick={() => setPage(curr + 1)} className={styles.pageBtn} title="Next"><ChevronRight size={16} /></button>
                <button disabled={curr === total} onClick={() => setPage(total)} className={styles.pageBtn} title="Last"><ChevronsRight size={16} /></button>
            </div>
        );
    };

    // const renderLegend = () => (
    //     <div className={styles.legendContainer}>
    //         <div className={styles.legendItem}>
    //             <div className={`${styles.legendDot} ${styles.dotExisting}`}></div>
    //             <span>Existing keywords</span>
    //         </div>
    //         {/* <div className={styles.legendItem}>
    //             <div className={`${styles.legendDot} ${styles.dotSelected}`}></div>
    //             <span>New (Selected)</span>
    //         </div>
    //         <div className={styles.legendItem}>
    //             <div className={`${styles.legendDot} ${styles.dotAvailable}`}></div>
    //             <span>Available</span>
    //         </div> */}
    //     </div>
    // );

    return (
        <div className={styles.container}>
            {/* Info Dialog & Header (No changes) */}
            {showInfo && (
                <div className={styles.infoOverlay} onClick={() => setShowInfo(false)}>
                    <div className={styles.infoBox} onClick={e => e.stopPropagation()}>
                        <div className={styles.infoHeader}>
                            <Info size={20} className={styles.infoIcon} />
                            <h3>How Keywords Work</h3>
                            <X size={18} className={styles.closeInfo} onClick={() => setShowInfo(false)} />
                        </div>
                        <div className={styles.infoContent}>
                            <p><strong><Database size={14} style={{ display: 'inline' }} /> Existing Keywords:</strong> <br />These keywords already exist in the Access Control Table and are pre-selected and cannot be deselected.</p>
                            <p><strong><Sparkles size={14} style={{ display: 'inline' }} /> New Keywords:</strong> <br />These are keywords that are not present in the Access Control Table and will only be indexed if selected here AND assigned to a user.</p>
                        </div>
                    </div>
                </div>
            )}

            <div className={styles.header}>
                <div className={styles.titleContainer}>
                    <h2 className={styles.title}>Select Keywords
                        <button className={styles.infoButton} onClick={() => setShowInfo(true)}><Info size={20} /></button>
                    </h2>
                    <div className={styles.stats}>
                        <span className={styles.statBadge}><Database size={14} /> {existingTotal} Existing</span>
                        <span className={styles.statBadge}><Sparkles size={14} /> {newTotal} Available</span>
                        <span className={styles.statBadge}><Check size={14} /> {selectedKeywords.size} Selected</span>
                    </div>
                </div>
                {/* {renderLegend()} */}
            </div>

            {/* {isNextDisabled && (
                <div className={styles.warningBanner}>
                    <AlertTriangle size={18} />
                    <span>No keywords available to index.</span>
                </div>
            )} */}

            <div className={styles.splitContainer}>
                {/* LEFT PANEL: AVAILABLE */}
                <div className={styles.panel}>
                    <div className={styles.panelHeader}>
                        <h3>Available ({newAvailableCount})</h3>
                        <div className={styles.bulkActions}>
                            {/* <button className={styles.bulkAction} disabled={true} style={{ opacity: 0.5, cursor: 'not-allowed' }} title="Existing keywords are auto-selected">
                                <Database size={12} /> Auto-Selected
                            </button>
                            <button onClick={selectFilteredNew} className={styles.bulkAction} disabled={newAvailableCount === 0} title="Select all filtered new">
                                <Sparkles size={12} />Select New
                            </button> */}
                            <button onClick={selectAllFiltered} className={styles.bulkAction} disabled={availableTotalCount === 0}>Select All</button>
                        </div>
                    </div>

                    <div className={styles.panelToolbar}>
                        <div className={styles.searchWrapper}>
                            <Search size={18} className={styles.searchIcon} />
                            <input
                                type="text"
                                placeholder="Filter available..."
                                value={availableSearch}
                                onChange={(e) => setAvailableSearch(e.target.value)}
                                className={styles.searchInput}
                            />
                            {availableSearch && <X size={18} className={styles.clearIcon} onClick={() => setAvailableSearch("")} />}
                        </div>
                        <button
                            onClick={() => setShowCustomThreshold(!showCustomThreshold)}
                            className={`${styles.filterToggleButton} ${isFilterActive ? styles.filterActive : ''}`}
                            title="Filter by Count"
                        >
                            <Filter size={18} />
                            {isFilterActive && <span className={styles.filterDot} />}
                        </button>
                    </div>

                    {showCustomThreshold && (
                        <div className={styles.inlineFilterOptions}>
                            <div className={styles.thresholdInputs}>
                                <div className={styles.thresholdGroup}>
                                    <label>Min</label>
                                    <input type="number" min={0} value={thresholdInput.min} onChange={e => setThresholdInput({ ...thresholdInput, min: e.target.value })} className={styles.thresholdInput} />
                                </div>
                                <div className={styles.thresholdGroup}>
                                    <label>Max</label>
                                    <input type="number" min={0} placeholder="Max" value={thresholdInput.max} onChange={e => setThresholdInput({ ...thresholdInput, max: e.target.value })} className={styles.thresholdInput} />
                                </div>
                                <button onClick={handleApplyFilter} className={styles.applyThresholdBtn}>Apply Filter</button>
                                <button onClick={handleClearFilter} className={styles.cancelThresholdBtn}>Clear Filter</button>
                            </div>
                        </div>
                    )}

                    <div className={styles.panelContent}>
                        {flatAvailableList.length === 0 ? (
                            <div className={styles.emptyState}>No matches found (Existing keywords are already selected)</div>
                        ) : (
                            <div className={styles.keywordList}>
                                {currentAvailableItems.map((item, index) => {
                                    // ✅ Collapsible Header Renderer
                                    if (item.type === 'header') {
                                        return (
                                            <div
                                                key={`header-${index}`}
                                                className={styles.collapsibleHeader}
                                                onClick={() => toggleSection(item.isExisting)}
                                            >
                                                <ChevronDown
                                                    size={16}
                                                    className={`${styles.headerChevron} ${!item.expanded ? styles.collapsed : ''}`}
                                                />
                                                <div className={styles.headerLabel}>
                                                    {item.isExisting ? <Database size={14} /> : <Sparkles size={14} />}
                                                    {item.label}
                                                </div>
                                            </div>
                                        );
                                    }
                                    return (
                                        <div key={item.keyword} className={`${styles.keywordCard} ${item.isExisting ? styles.existingCard : styles.newCard}`} onClick={() => toggleKeyword(item.keyword, true)}>
                                            <span className={styles.keywordName}>{item.keyword}</span>
                                            {existingKeywords.size == 0 && <span className={styles.keywordCount}>{item.count} files</span>}
                                            <Plus size={16} className={styles.addIcon} />
                                        </div>
                                    );
                                })}
                            </div>
                        )}
                    </div>
                    {renderPagination(availablePage, availableTotalPages, setAvailablePage)}
                </div>

                {/* Transfer Controls */}
                <div className={styles.transferColumn}>
                    <button onClick={selectAllFiltered} className={styles.transferBtn} disabled={availableTotalCount === 0}><ChevronRight size={20} /></button>
                    <button onClick={deselectAllFiltered} className={styles.transferBtn} disabled={flatSelectedList.length === 0}><ChevronLeft size={20} /></button>
                </div>

                {/* RIGHT PANEL: SELECTED */}
                <div className={styles.panel}>
                    <div className={styles.panelHeader}>
                        <h3>Searchable Keyword ({selectedKeywords.size})</h3>
                        <button onClick={deselectAllFiltered} className={styles.panelAction} disabled={flatSelectedList.length === 0}>
                            {selectedSearch || selectedFilter !== 'ALL' ? "Deselect Visible" : "Deselect All"}
                        </button>
                    </div>

                    <div className={styles.panelToolbar} style={{ flexDirection: 'column', alignItems: 'stretch', gap: '10px' }}>
                        <div className={styles.searchWrapper}>
                            <Search size={18} className={styles.searchIcon} />
                            <input
                                type="text"
                                placeholder="Filter selected..."
                                value={selectedSearch}
                                onChange={(e) => setSelectedSearch(e.target.value)}
                                className={styles.searchInput}
                            />
                            {selectedSearch && <X size={18} className={styles.clearIcon} onClick={() => setSelectedSearch("")} />}
                        </div>

                        {/* ✅ NEW: Filter Buttons */}
                        <div className={styles.filterGroup}>
                            <button
                                className={`${styles.filterBtn} ${selectedFilter === 'ALL' ? styles.filterBtnActive : ''}`}
                                onClick={() => { setSelectedFilter('ALL'); setSelectedPage(1); }}
                            >
                                All
                            </button>
                            <button
                                className={`${styles.filterBtn} ${selectedFilter === 'EXISTING' ? styles.filterBtnActive : ''}`}
                                onClick={() => { setSelectedFilter('EXISTING'); setSelectedPage(1); }}
                            >
                                <Database size={12} style={{ marginRight: 4 }} /> Existing
                            </button>
                            <button
                                className={`${styles.filterBtn} ${selectedFilter === 'NEW' ? styles.filterBtnActive : ''}`}
                                onClick={() => { setSelectedFilter('NEW'); setSelectedPage(1); }}
                            >
                                <Sparkles size={12} style={{ marginRight: 4 }} /> New
                            </button>
                        </div>
                    </div>

                    <div className={styles.panelContent}>
                        {flatSelectedList.length === 0 ? (
                            <div className={styles.emptyState}>
                                {selectedKeywords.size > 0 ? "No keywords match current filter" : "Select keywords to assign access"}
                            </div>
                        ) : (
                            <div className={styles.selectedList}>
                                {currentSelectedItems.map((item) => {
                                    const isLocked = item.isExisting;

                                    return (
                                        <div
                                            key={item.keyword}
                                            className={`${styles.selectedCard} ${item.isExisting ? styles.existingSelected : ''}`}
                                            style={isLocked ? { cursor: 'default', opacity: 0.9, backgroundColor: '#fff7ed', borderColor: '#fdba74' } : {}}
                                            onClick={() => !isLocked && toggleKeyword(item.keyword, false)}
                                        >
                                            <Check size={14} className={styles.checkIcon} />
                                            <span className={styles.keywordName}>{item.keyword}</span>
                                            {existingKeywords.size == 0 && <span className={styles.keywordCount}>{item.count} files</span>}

                                            {isLocked ? (
                                                <Lock size={14} className={styles.removeIcon} style={{ color: '#ea580c' }} />
                                            ) : (
                                                <X size={16} className={styles.removeIcon} />
                                            )}
                                        </div>
                                    );
                                })}
                            </div>
                        )}
                    </div>
                    {renderPagination(selectedPage, selectedTotalPages, setSelectedPage)}
                </div>
            </div>

            <div className={styles.actionButtons}>
                <button onClick={onBack} className={styles.backButton}>Back</button>
                <button onClick={onNext} disabled={isNextDisabled} className={styles.nextButton}>Initialize Access</button>
            </div>
        </div>
    );
}