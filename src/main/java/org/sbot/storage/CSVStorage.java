package org.sbot.storage;

import org.sbot.alerts.Alert;

import java.util.List;

public final class CSVStorage extends AbstractAsyncStorage {

    @Override
    protected List<Alert> loadAlerts() {
        //TODO
        return null;
    }

    @Override
    protected boolean saveAlerts() {
        //TODO
        return true;
    }

    /* chat gpt

    private List<Alert> getStoredAlerts() {
        // Implémentez la récupération des alertes depuis le fichier CSV
        try (Reader reader = new FileReader("alerts.csv")) {
            return new CsvToBeanBuilder<Alert>(reader)
                    .withType(Alert.class)
                    .build()
                    .parse();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    private void writeAlertsToFile(List<Alert> alerts) {
        // Implémentez l'écriture des alertes dans le fichier CSV
        try (Writer writer = new FileWriter("alerts.csv");
             CSVWriter csvWriter = new CSVWriter(writer, CSVWriter.DEFAULT_SEPARATOR, CSVWriter.NO_QUOTE_CHARACTER,
                     CSVWriter.DEFAULT_ESCAPE_CHARACTER, CSVWriter.DEFAULT_LINE_END)) {

            // Écrire les en-têtes si le fichier est vide
            if (alerts.isEmpty()) {
                csvWriter.writeNext(new String[]{"crypto", "low", "high", "message"});
            }

            // Écrire chaque alerte dans le fichier
            for (Alert alert : alerts) {
                csvWriter.writeNext(new String[]{alert.getCrypto(), String.valueOf(alert.getLow()),
                        String.valueOf(alert.getHigh()), alert.getMessage()});
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
     */
}
