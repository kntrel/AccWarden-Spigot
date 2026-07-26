package com.kntrel.mc.accwarden.gateway;

import com.kntrel.mc.accwarden.gateway.policy.Finding;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public sealed interface Decision {

    List<Finding> findings();

    record Pass(List<Finding> findings) implements Decision {

        public Pass {
            findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
        }

        public Pass() {
            this(List.of());
        }
    }

    record Throttled(Penalty penalty, List<Finding> findings) implements Decision {

        public Throttled {
            Objects.requireNonNull(penalty, "penalty");
            findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
            if (findings.isEmpty()) {
                throw new IllegalArgumentException("A throttled decision requires at least one cause.");
            }
            if (!findings.contains(penalty.cause())) {
                throw new IllegalArgumentException("The penalty cause must be present in the decision findings.");
            }
        }
    }

}
