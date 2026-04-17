package com.c446.disctui_server.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WireProtocolTest {

    @Test
    void decodeDiscoverPacketRoundTrip() {
        DiscoverPacket discover = new DiscoverPacket("TUICord", "v1", 2, 10, 45);
        byte[] wire = IByteBufferTransmutable.toWirePacket(
                IByteBufferTransmutable.WireType.DISCOVER,
                discover.toByteArray()
        );

        IByteBufferTransmutable.WireEnvelope envelope = IByteBufferTransmutable.fromWirePacket(wire);
        Object decoded = IByteBufferTransmutable.decodeByType(envelope.type(), envelope.payload());

        assertInstanceOf(DiscoverPacket.class, decoded);
        DiscoverPacket parsed = (DiscoverPacket) decoded;
        assertEquals(discover, parsed);
    }

    @Test
    void decodeChannelSelectPacketRoundTrip() {
        ChannelSelectPacket selectPacket = new ChannelSelectPacket(123L);
        byte[] wire = IByteBufferTransmutable.toWirePacket(
                IByteBufferTransmutable.WireType.CHANNEL_SELECT,
                selectPacket.toByteArray()
        );

        IByteBufferTransmutable.WireEnvelope envelope = IByteBufferTransmutable.fromWirePacket(wire);
        Object decoded = IByteBufferTransmutable.decodeByType(envelope.type(), envelope.payload());

        assertInstanceOf(ChannelSelectPacket.class, decoded);
        ChannelSelectPacket parsed = (ChannelSelectPacket) decoded;
        assertEquals(123L, parsed.channelId());
    }
}

