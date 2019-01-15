
/*
 * Copyright (C) 2018-2019 Edward Lu
 *
 * This file is subject to the terms and conditions of the GNU Lesser
 * General Public License v2.1. See the file LICENSE in the top level
 * directory for more details.
 */

package NDNLiteSupport.SignOnBasicControllerBLE.secureSignOn;

public class SignOnControllerConsts {
    // the prefix of the name of the KD public key NDN certificate generated by the controller
    // (the full name of the certificate is this prefix + the device identifier in hex string format,
    //  i.e. "/sign-on/cert/000102030405060708090001")
    public static final String KD_PUB_CERTIFICATE_NAME_PREFIX = "/sign-on/cert/";
}