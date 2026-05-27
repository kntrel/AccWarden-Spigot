package com.kntrel.mc.accwarden.account.link;

public enum AccountLinkResult {

    ALREADY_LINKED,
    NO_LINK,
    @Deprecated
    NOT_LINKED,
    LINKED_TO_JAVA,
    LINKED_TO_BEDROCK;

    public boolean isNoOp() {
        return this == ALREADY_LINKED || this == NO_LINK || this == NOT_LINKED;
    }

}
