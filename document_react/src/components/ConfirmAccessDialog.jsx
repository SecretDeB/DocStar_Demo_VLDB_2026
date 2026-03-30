import { useEffect, useRef } from 'react';

import styles from './AccessDashboard.module.css';

export function ConfirmAccessDialog({ isOpen, onClose, onConfirm, data }) {
  const dialogRef = useRef(null);

  // Logic to programmatically show/hide the native <dialog> element
  useEffect(() => {
    const dialog = dialogRef.current;
    if (dialog) {
      if (isOpen) {
        dialog.showModal();
      } else {
        dialog.close();
      }
    }
  }, [isOpen]);

  if (!isOpen) {
    return null;
  }

  return (
    <dialog ref={dialogRef} onCancel={onClose} className={styles.dialog}>
      <h3 className={styles.dialogTitle}>
        {data.mode === 'grant' ? 'Confirm Grant' : 'Confirm Revoke'}
      </h3>
      <div className={styles.dialogContent}>
        <p>You are about to <strong>{data.mode}</strong> access for the following keywords:</p>
        <ul className={styles.dialogList}>
          {data.keywords.map(kw => <li key={kw}>{kw}</li>)}
        </ul>
        <p>{data.mode === 'grant' ? 'To' : 'From'} the following clients:</p>
        <ul className={styles.dialogList}>
          {data.clients.map(cid => <li key={cid}>{data.clientMap[cid] || cid}</li>)}
        </ul>
        <p>Are you sure you want to proceed?</p>
      </div>
      <div className={styles.dialogActions}>
        <button className={styles.secondaryButton} onClick={onClose}>
          No, Cancel
        </button>
        <button className={styles.actionButton} onClick={onConfirm}>
          Yes, {data.mode === 'grant' ? 'Grant' : 'Revoke'}
        </button>
      </div>
    </dialog>
  );
}