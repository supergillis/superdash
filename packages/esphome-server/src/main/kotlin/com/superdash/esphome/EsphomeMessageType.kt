package com.superdash.esphome

/** Wire message-type IDs from `api.proto`'s `option (id) = N;` annotations on
 *  `service Api { rpc ... }` method definitions. ESPHome's frame format pairs
 *  each protobuf payload with the RPC method's numeric id; this is NOT the same
 *  as the protobuf message's tag.
 *
 *  These IDs are HAND-MIRRORED because protobuf's lite runtime does not carry
 *  service-method option metadata into generated code. There's no `Api.HelloRequest.METHOD_ID`
 *  constant we can reference. protoc-lite drops the `option (id) = N` annotation entirely.
 *  The full (non-lite) runtime would expose it via FileDescriptor reflection, but that's
 *  another ~1MB of runtime weight on Android.
 *
 *  Source of truth: `packages/esphome-server/src/main/proto-pristine/api.proto`
 *  `option (id) = N;` annotations. */
internal object EsphomeMessageType {
    const val HELLO_REQUEST = 1
    const val HELLO_RESPONSE = 2

    const val DISCONNECT_REQUEST = 5
    const val DISCONNECT_RESPONSE = 6
    const val PING_REQUEST = 7
    const val PING_RESPONSE = 8
    const val DEVICE_INFO_REQUEST = 9
    const val DEVICE_INFO_RESPONSE = 10
    const val LIST_ENTITIES_REQUEST = 11
    const val LIST_ENTITIES_DONE_RESPONSE = 19
    const val SUBSCRIBE_STATES_REQUEST = 20

    const val LIST_ENTITIES_BINARY_SENSOR_RESPONSE = 12
    const val BINARY_SENSOR_STATE_RESPONSE = 21

    const val LIST_ENTITIES_SENSOR_RESPONSE = 16
    const val SENSOR_STATE_RESPONSE = 25

    const val LIST_ENTITIES_TEXT_SENSOR_RESPONSE = 18
    const val TEXT_SENSOR_STATE_RESPONSE = 27

    // Verified against esphome/api.proto @ 2026.4.5.
    // Earlier draft had 14/23, which point to Fan responses.
    const val LIST_ENTITIES_SWITCH_RESPONSE = 17
    const val SWITCH_STATE_RESPONSE = 26
    const val SWITCH_COMMAND_REQUEST = 33

    const val LIST_ENTITIES_NUMBER_RESPONSE = 49
    const val NUMBER_STATE_RESPONSE = 50
    const val NUMBER_COMMAND_REQUEST = 51

    const val LIST_ENTITIES_SELECT_RESPONSE = 52
    const val SELECT_STATE_RESPONSE = 53
    const val SELECT_COMMAND_REQUEST = 54

    const val LIST_ENTITIES_BUTTON_RESPONSE = 61
    const val BUTTON_COMMAND_REQUEST = 62
}
