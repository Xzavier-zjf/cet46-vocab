package com.cet46.vocab.security;

import java.util.List;
import java.util.Set;

public final class GlobalCloudModelPermissions {

    public static final String CREATE = "GLOBAL_CLOUD_MODEL_CREATE";
    public static final String EDIT = "GLOBAL_CLOUD_MODEL_EDIT";
    public static final String DELETE = "GLOBAL_CLOUD_MODEL_DELETE";

    public static final List<String> ORDERED = List.of(CREATE, EDIT, DELETE);
    public static final Set<String> ALL = Set.copyOf(ORDERED);

    private GlobalCloudModelPermissions() {
    }
}
