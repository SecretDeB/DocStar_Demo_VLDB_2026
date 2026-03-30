import React from 'react';
import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from "lucide-react";
import styles from './Pagination.module.css';

const Pagination = ({
    currentPage,
    totalItems,
    itemsPerPage,
    onPageChange,
    onItemsPerPageChange
}) => {
    const totalPages = Math.ceil(totalItems / itemsPerPage);
    const startItem = totalItems === 0 ? 0 : (currentPage - 1) * itemsPerPage + 1;
    const endItem = Math.min(currentPage * itemsPerPage, totalItems);

    // Logic to generate page numbers with ellipsis
    const getPageNumbers = () => {
        const pages = [];
        const maxVisiblePages = 5;

        if (totalPages <= maxVisiblePages) {
            for (let i = 1; i <= totalPages; i++) {
                pages.push(i);
            }
        } else {
            if (currentPage <= 3) {
                for (let i = 1; i <= 4; i++) pages.push(i);
                pages.push('...');
                pages.push(totalPages);
            } else if (currentPage >= totalPages - 2) {
                pages.push(1);
                pages.push('...');
                for (let i = totalPages - 3; i <= totalPages; i++) pages.push(i);
            } else {
                pages.push(1);
                pages.push('...');
                for (let i = currentPage - 1; i <= currentPage + 1; i++) pages.push(i);
                pages.push('...');
                pages.push(totalPages);
            }
        }
        return pages;
    };

    const handlePageClick = (page) => {
        // Scroll to top of table/dashboard when changing pages
        window.scrollTo({ top: 0, behavior: 'smooth' });
        onPageChange(page);
    };

    if (totalItems === 0) return null;

    return (
        <div className={styles.paginationContainer}>
            <div className={styles.paginationInfo}>
                <span>
                    Showing {startItem} to {endItem} of {totalItems} documents
                </span>
                <div className={styles.itemsPerPageSelector}>
                    <label htmlFor="itemsPerPage">Items per page:</label>
                    <select
                        id="itemsPerPage"
                        value={itemsPerPage}
                        onChange={(e) => onItemsPerPageChange(Number(e.target.value))}
                        className={styles.itemsPerPageSelect}
                    >
                        <option value={5}>5</option>
                        <option value={10}>10</option>
                        <option value={20}>20</option>
                        <option value={50}>50</option>
                        <option value={100}>100</option>
                    </select>
                </div>
            </div>

            <div className={styles.paginationControls}>
                <button
                    onClick={() => handlePageClick(1)}
                    disabled={currentPage === 1}
                    className={`${styles.paginationButton} ${styles.paginationButtonIcon}`}
                    title="First page"
                >
                    <ChevronsLeft size={18} />
                </button>

                <button
                    onClick={() => handlePageClick(currentPage - 1)}
                    disabled={currentPage === 1}
                    className={`${styles.paginationButton} ${styles.paginationButtonIcon}`}
                    title="Previous page"
                >
                    <ChevronLeft size={18} />
                </button>

                <div className={styles.paginationNumbers}>
                    {getPageNumbers().map((page, index) => (
                        page === '...' ? (
                            <span key={`ellipsis-${index}`} className={styles.paginationEllipsis}>
                                ...
                            </span>
                        ) : (
                            <button
                                key={page}
                                onClick={() => handlePageClick(page)}
                                className={`${styles.paginationButton} ${styles.paginationNumber} ${currentPage === page ? styles.paginationNumberActive : ''
                                    }`}
                            >
                                {page}
                            </button>
                        )
                    ))}
                </div>

                <button
                    onClick={() => handlePageClick(currentPage + 1)}
                    disabled={currentPage === totalPages}
                    className={`${styles.paginationButton} ${styles.paginationButtonIcon}`}
                    title="Next page"
                >
                    <ChevronRight size={18} />
                </button>

                <button
                    onClick={() => handlePageClick(totalPages)}
                    disabled={currentPage === totalPages}
                    className={`${styles.paginationButton} ${styles.paginationButtonIcon}`}
                    title="Last page"
                >
                    <ChevronsRight size={18} />
                </button>
            </div>
        </div>
    );
};

export default Pagination;