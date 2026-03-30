import styles from "./FormField.module.css";

export default function FormField({
  name,
  type = "text",
  value,
  onChange,
  placeholder,
  error,
}) {
  return (
    <div className={styles.fieldContainer}>
      <input
        name={name}
        type={type}
        value={value}
        onChange={onChange}
        required
        title={`Please enter a${["address", "email"].includes(name) ? "n" : ""} ${name} ${name === "phone" ? "number" : ""}here`}
        placeholder={placeholder}
        className={`${styles.inputField} ${error ? styles.inputError : ""}`}
      />
      {error && <div className={styles.errorText}>{error}</div>}
    </div>
  );
}
