package com.gemalto.chaos.container;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gemalto.chaos.ChaosException;
import com.gemalto.chaos.container.enums.ContainerHealth;
import com.gemalto.chaos.experiment.Experiment;
import com.gemalto.chaos.experiment.ExperimentalObject;
import com.gemalto.chaos.experiment.enums.ExperimentType;
import com.gemalto.chaos.experiment.impl.GenericContainerExperiment;
import com.gemalto.chaos.notification.datadog.DataDogIdentifier;
import com.gemalto.chaos.platform.Platform;
import com.gemalto.chaos.platform.ShellBasedExperiment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.validation.constraints.NotNull;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

import static com.gemalto.chaos.constants.DataDogConstants.DATADOG_CONTAINER_KEY;
import static com.gemalto.chaos.util.MethodUtils.getMethodsWithAnnotation;
import static net.logstash.logback.argument.StructuredArguments.v;

public abstract class Container implements ExperimentalObject {
    protected final transient Logger log = LoggerFactory.getLogger(getClass());
    private final List<ExperimentType> supportedExperimentTypes = new ArrayList<>();
    private ContainerHealth containerHealth;
    private Method lastExperimentMethod;
    private Experiment currentExperiment;
    protected final Map<String, String> dataDogTags = new HashMap<>();

    protected Container () {
        for (ExperimentType experimentType : ExperimentType.values()) {
            if (!getMethodsWithAnnotation(this.getClass(), experimentType.getAnnotation()).isEmpty()) {
                supportedExperimentTypes.add(experimentType);
            }
        }
    }

    @Override
    public boolean canExperiment () {
        if(!supportedExperimentTypes.isEmpty() && new Random().nextDouble() < getPlatform().getDestructionProbability()){
            return true;
        }
        log.debug("Cannot experiment on the container right now", v(DATADOG_CONTAINER_KEY, this));
        return false;
    }

    @JsonIgnore
    public abstract Platform getPlatform ();

    @JsonIgnore
    public List<ExperimentType> getSupportedExperimentTypes () {
        return supportedExperimentTypes;
    }

    @Override
    public boolean equals (Object o) {
        if (o == null || o.getClass() != this.getClass()) {
            return false;
        }
        Container other = (Container) o;
        return this.getIdentity() == other.getIdentity();
    }

    /**
     * Uses all the fields in the container implementation (but not the Container parent class)
     * to create a checksum of the container. This checksum should be immutable and can be used
     * to recognize when building a roster if the container already exists, and can reference
     * the same object.
     *
     * @return A checksum (format long) of the class based on the implementation specific fields
     */
    public long getIdentity () {
        StringBuilder identity = new StringBuilder();
        for (Field field : this.getClass().getDeclaredFields()) {
            if (Modifier.isTransient(field.getModifiers())) continue;
            if (field.isSynthetic()) continue;
            field.setAccessible(true);
            try {
                if (field.get(this) != null) {
                    if (identity.length() > 1) {
                        identity.append("$$$$$");
                    }
                    identity.append(field.get(this).toString());
                }
            } catch (IllegalAccessException e) {
                log.error("Caught IllegalAccessException ", e);
            }
        }
        byte[] primitiveByteArray = identity.toString().getBytes();
        CRC32 checksum = new CRC32();
        checksum.update(primitiveByteArray);
        return checksum.getValue();
    }

    @Override
    public String toString () {
        StringBuilder output = new StringBuilder();
        output.append("Container type: ");
        output.append(this.getClass().getSimpleName());
        Arrays.stream(this.getClass().getDeclaredFields())
              .filter(field -> !Modifier.isTransient(field.getModifiers()))
              .filter(field -> !field.isSynthetic())
              .forEachOrdered(field -> {
                  field.setAccessible(true);
                  try {
                      output.append("\n\t");
                      output.append(field.getName());
                      output.append(":\t");
                      output.append(field.get(this));
                  } catch (IllegalAccessException e) {
                      log.error("Could not read from field {}", field.getName(), e);
                  }
              });
        return output.toString();
    }

    public boolean supportsExperimentType (ExperimentType experimentType) {
        return supportedExperimentTypes.contains(experimentType);
    }

    public ContainerHealth getContainerHealth (ExperimentType experimentType) {
        updateContainerHealth(experimentType);
        return containerHealth;
    }

    private void updateContainerHealth (ExperimentType experimentType) {
        containerHealth = updateContainerHealthImpl(experimentType);
    }

    protected abstract ContainerHealth updateContainerHealthImpl (ExperimentType experimentType);

    public Experiment createExperiment () {
        currentExperiment = createExperiment(supportedExperimentTypes.get(new Random().nextInt(supportedExperimentTypes.size())));
        return currentExperiment;
    }

    public Experiment createExperiment (ExperimentType experimentType) {
        return GenericContainerExperiment.builder().withExperimentType(experimentType).withContainer(this).build();
    }

    public void startExperiment (Experiment experiment) {
        containerHealth = ContainerHealth.RUNNING_EXPERIMENT;
        log.info("Starting a experiment {} against container {}", experiment.getId(), this);
        experimentWithAnnotation(experiment);
    }

    @SuppressWarnings("unchecked")
    private void experimentWithAnnotation (Experiment experiment) {
        try {
            lastExperimentMethod = experiment.getExperimentMethod();
            lastExperimentMethod.invoke(this, experiment);
        } catch (IllegalAccessException | InvocationTargetException e) {
            log.error("Failed to run experiment {} on container {}: {}", experiment.getId(), this, e);
            throw new ChaosException(e);
        }
    }

    public void repeatExperiment (Experiment experiment) {
        if (lastExperimentMethod == null) {
            throw new ChaosException("Trying to repeat an experiment without having a prior one");
        }
        containerHealth = ContainerHealth.RUNNING_EXPERIMENT;
        try {
            lastExperimentMethod.invoke(this, experiment);
        } catch (InvocationTargetException | IllegalAccessException e) {
            throw new ChaosException(e);
        }
    }

    public abstract String getSimpleName ();

    public String getContainerType () {
        return this.getClass().getSimpleName();
    }

    @JsonIgnore
    public Duration getMinimumSelfHealingInterval () {
        return getPlatform().getMinimumSelfHealingInterval();
    }

    @JsonIgnore
    public abstract DataDogIdentifier getDataDogIdentifier ();

    boolean compareUniqueIdentifier (String uniqueIdentifier) {
        return uniqueIdentifier != null && compareUniqueIdentifierInner(uniqueIdentifier);
    }

    protected abstract boolean compareUniqueIdentifierInner (@NotNull String uniqueIdentifier);

    public void setMappedDiagnosticContext () {
        dataDogTags.forEach(MDC::put);
    }

    public void clearMappedDiagnosticContext () {
        dataDogTags.keySet().forEach(MDC::remove);
    }

    @JsonIgnore
    public Map<Class<? extends Annotation>, List<Method>> getExperimentMethods () {
        return Arrays.stream(ExperimentType.values())
                     .map(ExperimentType::getAnnotation)
                     .collect(Collectors.toMap(Function.identity(), (k) -> getMethodsWithAnnotation(this.getClass(), k)));
    }

    public final boolean supportsShellBasedExperiments () {
        return getPlatform() instanceof ShellBasedExperiment;
    }
}
