package com.cet46.vocab.security;

import java.util.List;
import java.util.Set;

public final class PrivateCloudModelPermissions {

    public static final String CREATE = "PRIVATE_CLOUD_MODEL_CREATE";
    public static final String EDIT = "PRIVATE_CLOUD_MODEL_EDIT";
    public static final String DELETE = "PRIVATE_CLOUD_MODEL_DELETE";
    public static final String TOGGLE = "PRIVATE_CLOUD_MODEL_TOGGLE";

    public static final List<String> ORDERED = List.of(CREATE, EDIT, DELETE, TOGGLE);
    public static final Set<String> ALL = Set.copyOf(ORDERED);

    private PrivateCloudModelPermissions() {
    }
}
