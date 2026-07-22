package com.meeplenote.play.internal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "player_group_members")
class PlayerGroupMemberEntity(
    @Column(name = "group_id", nullable = false)
    val groupId: Long,
    @Column(name = "player_id", nullable = false)
    val playerId: Long,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
