package com.cloud.acl;

import com.cloud.legacymodel.exceptions.PermissionDeniedException;
import com.cloud.legacymodel.user.User;
import com.cloud.utils.component.Adapter;

// APIChecker checks the ownership and access control to API requests
public interface APIChecker extends Adapter {
    // Interface for checking access for a role using apiname
    // If true, apiChecker has checked the operation
    // If false, apiChecker is unable to handle the operation or not implemented
    // On exception, checkAccess failed don't allow
    boolean checkAccess(User user, String apiCommandName) throws PermissionDeniedException;
}
