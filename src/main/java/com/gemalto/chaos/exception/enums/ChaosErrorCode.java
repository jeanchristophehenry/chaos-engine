package com.gemalto.chaos.exception.enums;

import com.gemalto.chaos.exception.ErrorCode;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.ResourceBundle;

public enum ChaosErrorCode implements ErrorCode {
    GENERIC_FAILURE(10000),
    API_EXCEPTION(11000),

    INVALID_STATE(19001),
    NOTIFICATION_BUFFER_ERROR(18201),
    NOTIFICATION_BUFFER_RETRY_EXCEEDED(18202),
    PLATFORM_DOES_NOT_SUPPORT_RECYCLING(11001),
    SHELL_CLIENT_CONNECT_FAILURE(15001),
    SSH_CLIENT_INSTANTIATION_ERROR(15101),
    SSH_CLIENT_TRANSFER_ERROR(15102),
    SSH_CLIENT_COMMAND_ERROR(15103),
    ;
    private final int errorCode;
    private final String shortName;
    private final String message;
    private ResourceBundle translationBundle;

    ChaosErrorCode (int errorCode) {
        this.errorCode = errorCode;
        this.shortName = "errorCode." + errorCode + ".name";
        this.message = "errorCode." + errorCode + ".message";
    }

    @Override
    public int getErrorCode () {
        return errorCode;
    }

    @Override
    public String getMessage () {
        return message;
    }

    @Override
    public ResourceBundle getResourceBundle () {
        return Optional.ofNullable(translationBundle).orElseGet(this::initTranslationBundle);
    }

    private synchronized ResourceBundle initTranslationBundle () {
        if (translationBundle != null) return translationBundle;
        try {
            final Locale defaultLocale = Locale.getDefault();
            translationBundle = ResourceBundle.getBundle("exception.ChaosErrorCode", defaultLocale);
        } catch (MissingResourceException e) {
            translationBundle = ResourceBundle.getBundle("exception.ChaosErrorCode", Locale.US);
        }
        return translationBundle;
    }

    @Override
    public String getShortName () {
        return shortName;
    }

    @Override
    public void clearCachedResourceBundle () {
        translationBundle = null;
    }
}
