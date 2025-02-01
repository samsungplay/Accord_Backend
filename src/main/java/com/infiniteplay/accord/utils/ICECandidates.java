package com.infiniteplay.accord.utils;

import com.infiniteplay.accord.models.ICECandidate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ICECandidates {
    private List<ICECandidate> iceCandidates;
}
