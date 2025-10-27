package sandbox27.ila.backend.imports.besteschule;

import java.util.ArrayList;
import java.util.List;

public class ImportResult {
    
    private int total;
    private int updated;
    private int notFound;
    private int errors;
    private List<String> errorMessages;
    
    public ImportResult() {
        this.errorMessages = new ArrayList<>();
    }
    
    public int getTotal() {
        return total;
    }
    
    public void setTotal(int total) {
        this.total = total;
    }
    
    public int getUpdated() {
        return updated;
    }
    
    public void setUpdated(int updated) {
        this.updated = updated;
    }
    
    public void incrementUpdated() {
        this.updated++;
    }
    
    public int getNotFound() {
        return notFound;
    }
    
    public void setNotFound(int notFound) {
        this.notFound = notFound;
    }
    
    public void incrementNotFound() {
        this.notFound++;
    }
    
    public int getErrors() {
        return errors;
    }
    
    public void setErrors(int errors) {
        this.errors = errors;
    }
    
    public void incrementErrors() {
        this.errors++;
    }
    
    public List<String> getErrorMessages() {
        return errorMessages;
    }
    
    public void setErrorMessages(List<String> errorMessages) {
        this.errorMessages = errorMessages;
    }
    
    public void addErrorMessage(String message) {
        this.errorMessages.add(message);
    }
    
    public boolean hasErrors() {
        return errors > 0;
    }
    
    public String getSummary() {
        return String.format("Import abgeschlossen: %d gesamt, %d aktualisiert, %d nicht gefunden, %d Fehler",
                total, updated, notFound, errors);
    }
}
