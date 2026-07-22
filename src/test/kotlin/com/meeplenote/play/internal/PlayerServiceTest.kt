package com.meeplenote.play.internal

import com.meeplenote.common.api.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PlayerServiceTest {

    private val playerRepository = mock<PlayerRepository>()
    private val playerGroupRepository = mock<PlayerGroupRepository>()
    private val playerGroupMemberRepository = mock<PlayerGroupMemberRepository>()

    private val playerService = PlayerService(playerRepository, playerGroupRepository, playerGroupMemberRepository)

    private val userId = 1L

    private fun player(id: Long, name: String) = PlayerEntity(userId = userId, name = name).also {
        setId(it, id)
    }

    private fun group(id: Long, name: String) = PlayerGroupEntity(userId = userId, name = name).also {
        setId(it, id)
    }

    private fun setId(entity: Any, id: Long) {
        val field = entity.javaClass.getDeclaredField("id")
        field.isAccessible = true
        field.set(entity, id)
    }

    @Test
    fun `명부를 조회하면 즐겨찾기 이름순 정렬 결과에 소속 그룹이 배치 조회로 채워진다`() {
        val 철수 = player(1L, "철수")
        val 영희 = player(2L, "영희")
        whenever(playerRepository.findAllByUserIdOrderByIsFavoriteDescNameAsc(userId)).thenReturn(listOf(철수, 영희))
        whenever(playerGroupMemberRepository.findAllByPlayerIdIn(listOf(1L, 2L)))
            .thenReturn(listOf(PlayerGroupMemberEntity(groupId = 10L, playerId = 1L)))
        whenever(playerGroupRepository.findAllById(listOf(10L))).thenReturn(listOf(group(10L, "동아리")))

        val result = playerService.listPlayers(userId)

        assertThat(result).hasSize(2)
        assertThat(result[0].groups).containsExactly(PlayerGroupRef(10L, "동아리"))
        assertThat(result[1].groups).isEmpty()
    }

    @Test
    fun `플레이어가 없으면 그룹 배치 조회를 호출하지 않는다`() {
        whenever(playerRepository.findAllByUserIdOrderByIsFavoriteDescNameAsc(userId)).thenReturn(emptyList())

        val result = playerService.listPlayers(userId)

        assertThat(result).isEmpty()
        verify(playerGroupMemberRepository, never()).findAllByPlayerIdIn(any())
    }

    @Test
    fun `최근 함께 플레이한 사람을 limit과 함께 조회한다`() {
        whenever(playerRepository.findRecentlyPlayedWith(userId, 5)).thenReturn(listOf(player(1L, "철수")))

        val result = playerService.listRecentlyPlayedWith(userId, 5)

        assertThat(result).hasSize(1)
        verify(playerRepository).findRecentlyPlayedWith(userId, 5)
    }

    @Test
    fun `즐겨찾기를 토글하면 반영된 값을 반환한다`() {
        val 철수 = player(1L, "철수")
        whenever(playerRepository.findByUserIdAndId(userId, 1L)).thenReturn(철수)
        whenever(playerGroupMemberRepository.findAllByPlayerIdIn(listOf(1L))).thenReturn(emptyList())

        val result = playerService.setFavorite(userId, 1L, true)

        assertThat(result.isFavorite).isTrue()
    }

    @Test
    fun `존재하지 않거나 소유하지 않은 플레이어의 즐겨찾기를 변경하면 PLAYER_NOT_FOUND를 던진다`() {
        whenever(playerRepository.findByUserIdAndId(userId, 99L)).thenReturn(null)

        val ex = assertThrows<BusinessException> { playerService.setFavorite(userId, 99L, true) }

        assertThat(ex.code).isEqualTo("PLAYER_NOT_FOUND")
    }

    @Test
    fun `그룹을 생성하면 빈 멤버 목록으로 응답한다`() {
        whenever(playerGroupRepository.existsByUserIdAndName(userId, "동아리")).thenReturn(false)
        whenever(playerGroupRepository.saveAndFlush(any())).thenAnswer { invocation ->
            (invocation.getArgument<PlayerGroupEntity>(0)).also { setId(it, 5L) }
        }
        whenever(playerGroupMemberRepository.findAllByGroupIdIn(listOf(5L))).thenReturn(emptyList())

        val result = playerService.createGroup(userId, "동아리")

        assertThat(result.id).isEqualTo(5L)
        assertThat(result.players).isEmpty()
    }

    @Test
    fun `이미 존재하는 그룹 이름으로 생성하면 PLAYER_GROUP_NAME_DUPLICATE를 던진다`() {
        whenever(playerGroupRepository.existsByUserIdAndName(userId, "동아리")).thenReturn(true)

        val ex = assertThrows<BusinessException> { playerService.createGroup(userId, "동아리") }

        assertThat(ex.code).isEqualTo("PLAYER_GROUP_NAME_DUPLICATE")
        verify(playerGroupRepository, never()).saveAndFlush(any())
    }

    @Test
    fun `동시 요청으로 유니크 제약 위반이 발생해도 PLAYER_GROUP_NAME_DUPLICATE로 변환한다`() {
        whenever(playerGroupRepository.existsByUserIdAndName(userId, "동아리")).thenReturn(false)
        whenever(playerGroupRepository.saveAndFlush(any()))
            .thenThrow(org.springframework.dao.DataIntegrityViolationException("duplicate"))

        val ex = assertThrows<BusinessException> { playerService.createGroup(userId, "동아리") }

        assertThat(ex.code).isEqualTo("PLAYER_GROUP_NAME_DUPLICATE")
    }

    @Test
    fun `그룹 목록을 조회하면 소속 플레이어가 배치 조회로 채워진다`() {
        whenever(playerGroupRepository.findAllByUserId(userId)).thenReturn(listOf(group(5L, "동아리")))
        whenever(playerGroupMemberRepository.findAllByGroupIdIn(listOf(5L)))
            .thenReturn(listOf(PlayerGroupMemberEntity(groupId = 5L, playerId = 1L)))
        whenever(playerRepository.findAllById(listOf(1L))).thenReturn(listOf(player(1L, "철수")))

        val result = playerService.listGroups(userId)

        assertThat(result.single().players).containsExactly(PlayerGroupMemberRef(1L, "철수"))
    }

    @Test
    fun `그룹에 플레이어를 추가하면 멤버십을 저장한다`() {
        val theGroup = group(5L, "동아리")
        val 철수 = player(1L, "철수")
        whenever(playerGroupRepository.findByUserIdAndId(userId, 5L)).thenReturn(theGroup)
        whenever(playerRepository.findByUserIdAndId(userId, 1L)).thenReturn(철수)
        whenever(playerGroupMemberRepository.findByGroupIdAndPlayerId(5L, 1L)).thenReturn(null)
        whenever(playerGroupMemberRepository.findAllByGroupIdIn(listOf(5L))).thenReturn(emptyList())

        playerService.addPlayerToGroup(userId, 5L, 1L)

        verify(playerGroupMemberRepository).save(any())
    }

    @Test
    fun `이미 그룹에 속한 플레이어를 다시 추가해도 중복 저장하지 않는다`() {
        val theGroup = group(5L, "동아리")
        val 철수 = player(1L, "철수")
        whenever(playerGroupRepository.findByUserIdAndId(userId, 5L)).thenReturn(theGroup)
        whenever(playerRepository.findByUserIdAndId(userId, 1L)).thenReturn(철수)
        whenever(playerGroupMemberRepository.findByGroupIdAndPlayerId(5L, 1L))
            .thenReturn(PlayerGroupMemberEntity(groupId = 5L, playerId = 1L))
        whenever(playerGroupMemberRepository.findAllByGroupIdIn(listOf(5L))).thenReturn(emptyList())

        playerService.addPlayerToGroup(userId, 5L, 1L)

        verify(playerGroupMemberRepository, never()).save(any())
    }

    @Test
    fun `존재하지 않는 그룹에 플레이어를 추가하면 PLAYER_GROUP_NOT_FOUND를 던진다`() {
        whenever(playerGroupRepository.findByUserIdAndId(userId, 99L)).thenReturn(null)

        val ex = assertThrows<BusinessException> { playerService.addPlayerToGroup(userId, 99L, 1L) }

        assertThat(ex.code).isEqualTo("PLAYER_GROUP_NOT_FOUND")
    }

    @Test
    fun `타 유저 소유 플레이어를 그룹에 추가하면 PLAYER_NOT_FOUND를 던진다`() {
        whenever(playerGroupRepository.findByUserIdAndId(userId, 5L)).thenReturn(group(5L, "동아리"))
        whenever(playerRepository.findByUserIdAndId(userId, 1L)).thenReturn(null)

        val ex = assertThrows<BusinessException> { playerService.addPlayerToGroup(userId, 5L, 1L) }

        assertThat(ex.code).isEqualTo("PLAYER_NOT_FOUND")
    }

    @Test
    fun `그룹에서 플레이어를 제거한다`() {
        whenever(playerGroupRepository.findByUserIdAndId(userId, 5L)).thenReturn(group(5L, "동아리"))

        playerService.removePlayerFromGroup(userId, 5L, 1L)

        verify(playerGroupMemberRepository).deleteByGroupIdAndPlayerId(5L, 1L)
    }

    @Test
    fun `그룹을 삭제한다`() {
        val theGroup = group(5L, "동아리")
        whenever(playerGroupRepository.findByUserIdAndId(userId, 5L)).thenReturn(theGroup)

        playerService.deleteGroup(userId, 5L)

        verify(playerGroupRepository).delete(theGroup)
    }

    @Test
    fun `존재하지 않는 그룹을 삭제하면 PLAYER_GROUP_NOT_FOUND를 던진다`() {
        whenever(playerGroupRepository.findByUserIdAndId(userId, 99L)).thenReturn(null)

        val ex = assertThrows<BusinessException> { playerService.deleteGroup(userId, 99L) }

        assertThat(ex.code).isEqualTo("PLAYER_GROUP_NOT_FOUND")
    }
}
