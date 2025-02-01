package com.infiniteplay.accord.models;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ICECandidate {
 //{
    //    "candidate": "candidate:842163049 1 udp 1677729535 192.168.1.2 12345 typ host generation 0 ufrag 9g1y network-id 1",
    //    "sdpMid": "audio",
    //    "sdpMLineIndex": 0,
    //    "usernameFragment": "9g1y"
    //}
    private String candidate;
    private String sdpMid;
    private Integer sdpMLineIndex;

}
