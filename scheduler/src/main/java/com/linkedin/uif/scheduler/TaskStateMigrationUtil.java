package com.linkedin.uif.scheduler;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;

import com.google.common.collect.Lists;

import com.linkedin.uif.metastore.FsStateStore;
import com.linkedin.uif.metastore.StateStore;

/**
 * A utility for migrating task states due to a recent package change.
 *
 * <p>
 *     TODO: delete this class and the {@link TaskState} class after the migration is done.
 * </p>
 */
public class TaskStateMigrationUtil {

    /**
     * Migrate existing task states.
     *
     * @param fsUri File system URI
     * @param srcDir Source task state store directory
     * @param destDir Destination task state store directory
     */
    public static void migrate(String fsUri, String srcDir, String destDir) throws IOException {
        StateStore srcStore = new FsStateStore(fsUri, srcDir, TaskState.class);
        StateStore destStore = new FsStateStore(
                fsUri, destDir, com.linkedin.uif.runtime.TaskState.class);

        FileSystem fs = FileSystem.get(URI.create(fsUri), new Configuration());

        FileStatus[] stores = fs.listStatus(new Path(srcDir));
        if (stores == null || stores.length == 0) {
            System.out.println("No task states to migrate");
            return;
        }

        for (FileStatus store : stores) {
            String storeName = store.getPath().getName();
            FileStatus[] tables = fs.listStatus(store.getPath(), new PathFilter() {
                @Override
                public boolean accept(Path path) {
                    // We are only migrating task states
                    return path.getName().endsWith(".tst");
                }
            });
            if (tables == null || tables.length == 0) {
                System.out.println("No task states in store " + storeName);
                continue;
            }
            for (FileStatus table : tables) {
                String tableName = table.getPath().getName();
                List<TaskState> srcTaskStates = (List<TaskState>) srcStore.getAll(storeName, tableName);
                destStore.putAll(storeName, tableName, migrateTaskStates(srcTaskStates));
            }
        }
    }

    /**
     * Migrate individual task states.
     */
    private static List<com.linkedin.uif.runtime.TaskState> migrateTaskStates(
            List<TaskState> srcTaskStates) {

        List<com.linkedin.uif.runtime.TaskState> destTaskStates = Lists.newArrayList();
        for (TaskState src : srcTaskStates) {
            com.linkedin.uif.runtime.TaskState dest = new com.linkedin.uif.runtime.TaskState(src);
            dest.addAll(src);
            dest.setStartTime(src.getStartTime());
            dest.setEndTime(src.getEndTime());
            dest.setTaskDuration(src.getTaskDuration());
            dest.setHighWaterMark(src.getHighWaterMark());
            dest.setWorkingState(src.getWorkingState());
            destTaskStates.add(dest);
        }

        return destTaskStates;
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.out.println("Usage: TaskStateMigrationUtil <fs uri> <src store dir> <dest store dir>");
            System.exit(1);
        }

        migrate(args[0], args[1], args[2]);
    }
}
