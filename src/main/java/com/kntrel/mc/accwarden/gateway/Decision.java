package com.kntrel.mc.accwarden.gateway;

import java.util.List;
import java.util.Objects;

public sealed interface Decision {

    //CONTRACT
    List<Notification> notifications();


    //IMPLEMENTATIONS
    record Pass(List<Notification> notifications) implements Decision {

        public Pass {
            notifications = notifications == null
                    ? List.of()
                    : List.copyOf(notifications);
        }

        public Pass() {
            this(null);
        }
    }

    record Throttled(Penalty penalty, List<Notification> notifications) implements Decision {

        public Throttled {
            Objects.requireNonNull(penalty, "penalty");
            notifications = notifications == null
                    ? List.of()
                    : List.copyOf(notifications);
        }


        public Throttled(Penalty penalty) {
            this(penalty, null);
        }
    }

}
