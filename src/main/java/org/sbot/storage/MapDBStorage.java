package org.sbot.storage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.sbot.alerts.Alert;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;


public abstract class MapDBStorage {

    private static final Logger LOGGER = LogManager.getLogger(MapDBStorage.class);

//    private final DB db;

    protected MapDBStorage() {
//        db = DBMaker.memoryDB().make();;
//        try (HTreeMap<String, Alert> myMap = db.hashMap("myMap", QueueLong.Node.SERIALIZER.STRING, ).createOrOpen()) {
//        }
    }

/*    public class CustomSerializer implements Serializer<Alert> {

        @Override
        public void serialize(DataOutput2 out, Alert value) throws IOException {
            // Écrire les champs de la classe dans l'ordre
            out.writeUTF(value.getString1());
            out.writeUTF(value.getString2());
            out.writeBigDecimal(value.getBigDecimal1());
            out.writeBigDecimal(value.getBigDecimal2());
            out.writeBigDecimal(value.getBigDecimal3());
        }

        @Override
        public Alert deserialize(DataInput2 input, int available) throws IOException {
            // Lire les champs de la classe dans l'ordre
            String string1 = input.readUTF();
            String string2 = input.readUTF();
            BigDecimal bigDecimal1 = input.readBigDecimal();
            BigDecimal bigDecimal2 = input.readBigDecimal();
            BigDecimal bigDecimal3 = input.readBigDecimal();

            // Créer et retourner une nouvelle instance de la classe avec les valeurs lues
            return null;
        }

        @Override
        public int fixedSize() {
            // Retourner -1 pour indiquer que la taille n'est pas fixe
            return -1;
        }
    }
    */
}
