package com.gemalto.chaos.util;

import org.junit.Test;

import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static java.util.UUID.randomUUID;
import static org.junit.Assert.*;

public class AwsRDSUtilsTest {
    @Test
    public void generateSnapshotName () {
        IntStream.range(0, 100).parallel().forEach(i -> {
            String identifier = randomUUID().toString();
            String snapshotName = AwsRDSUtils.generateSnapshotName(identifier);
            assertTrue(AwsRDSUtils.isChaosSnapshot(snapshotName));
        });
        IntStream.range(0, 100).parallel().forEach(i -> {
            String identifier = randomUUID().toString() + "-";
            String snapshotName = AwsRDSUtils.generateSnapshotName(identifier);
            assertTrue(AwsRDSUtils.isChaosSnapshot(snapshotName));
        });
        IntStream.range(0, 100).parallel().forEach(i -> {
            String identifier = randomUUID().toString() + "--" + randomUUID().toString();
            String snapshotName = AwsRDSUtils.generateSnapshotName(identifier);
            assertTrue(AwsRDSUtils.isChaosSnapshot(snapshotName));
        });
        IntStream.range(0, 100).parallel().forEach(i -> {
            StringBuilder identifier = new StringBuilder(randomUUID().toString());
            while (identifier.length() < 300) {
                identifier.append(randomUUID().toString());
            }
            String snapshotName = AwsRDSUtils.generateSnapshotName(identifier.toString());
            assertTrue(AwsRDSUtils.isChaosSnapshot(snapshotName));
        });
    }

    @Test
    public void rdsSnapshotNameConstraints () {

        /*
        https://docs.aws.amazon.com/cli/latest/reference/rds/create-db-snapshot.html

            Constraints:

            Can't be null, empty, or blank
            Must contain from 1 to 255 letters, numbers, or hyphens
            First character must be a letter
            Can't end with a hyphen or contain two consecutive hyphens
         */
        IntStream.range(0, 100)
                 .parallel()
                 .mapToObj(i -> AwsRDSUtils.generateSnapshotName(randomUUID().toString()))
                 .forEach(s -> {
                     assertTrue("Snapshot name cannot exceed 255 letters", s.length() <= 255);
                     assertNotNull("Snapshot name cannot be null", s);
                     assertNotEquals("Snapshot name cannot be blank", "", s);
                     assertFalse("Cannot contain two consecutive hypens", s.contains("--"));
                     assertFalse("Cannot end with a hypen", s.endsWith("-"));
                     assertTrue("Unexpected name pattern (Can contain letters, numbers, and hyphens)" + s, Pattern.compile("^[A-Za-z0-9-]+$")
                                                                                                                  .matcher(s)
                                                                                                                  .matches());
                 });
    }
}