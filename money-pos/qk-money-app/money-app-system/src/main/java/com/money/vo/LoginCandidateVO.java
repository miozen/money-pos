package com.money.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Minimum account metadata required by the local Admin login selector. */
@Data
@AllArgsConstructor
public class LoginCandidateVO {
    private String username;
    private String displayName;
}
