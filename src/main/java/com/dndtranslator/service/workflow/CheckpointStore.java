package com.dndtranslator.service.workflow;

import java.util.Optional;

/**
 * Persistencia de checkpoints para resume de traduccion.
 */
public interface CheckpointStore {

    Optional<CheckpointSnapshot> load(String jobKey);

    void save(CheckpointSnapshot snapshot);

    void clear(String jobKey);

    static CheckpointStore noop() {
        return NoOpCheckpointStore.INSTANCE;
    }

    enum NoOpCheckpointStore implements CheckpointStore {
        INSTANCE;

        @Override
        public Optional<CheckpointSnapshot> load(String jobKey) {
            return Optional.empty();
        }

        @Override
        public void save(CheckpointSnapshot snapshot) {
            // noop
        }

        @Override
        public void clear(String jobKey) {
            // noop
        }
    }
}

