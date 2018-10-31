package com.example.chenbo.helloworld;

public class SendingPacketState {

    byte[] currentSendingByteArray = {};
    boolean sendingOutFragmentsMode = false;
    int fragmentOffset = 0;

    byte[] currentInterestByteArrayToSend = {};
}
