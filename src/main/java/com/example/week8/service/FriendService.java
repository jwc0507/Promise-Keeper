package com.example.week8.service;
import com.example.week8.domain.Friend;
import com.example.week8.domain.Member;
import com.example.week8.dto.request.FriendAdditionRequestDto;
import com.example.week8.dto.response.FriendInfoResponseDto;
import com.example.week8.dto.response.ResponseDto;
import com.example.week8.repository.FriendRepository;
import com.example.week8.repository.MemberRepository;
import com.example.week8.security.TokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class FriendService {

    private final FriendRepository friendRepository;
    private final MemberRepository memberRepository;
    private final TokenProvider tokenProvider;

    //친구 목록 조회
    @Transactional
    public ResponseDto<?> getFriendList(HttpServletRequest request){

        ResponseDto<?> chkResponse = validateCheck(request);
        if(!chkResponse.isSuccess())
            return chkResponse;
        Member member = (Member) chkResponse.getData();
        /*
        System.out.println("2");
        List<Friend> friendList = member.getFriendList(); //friendRepository 활용해서 db에서 find 하지 않고, member의 friendList를 get하는 방식으로 코딩해도 정상 작동하겠지?
        System.out.println("0번째 친구의 ownerID : "+ friendList.get(0).getOwner());
        System.out.println("1번째 친구의 ownerID : "+ friendList.get(1).getOwner());
         */
        List<Friend> friendList = friendRepository.findAllByOwner(member);
        List<FriendInfoResponseDto> friendInfoResponseDtoList = new ArrayList<>();
        for(Friend friend : friendList)
        {
            friendInfoResponseDtoList.add(FriendInfoResponseDto.builder()
                            .nickname(friend.getFriend().getNickname())
                            .creditScore(friend.getFriend().getCredit())
                            .build()
            );
        }
        return ResponseDto.success(friendInfoResponseDtoList);
    }

    //닉네임으로 친구 추가
    @Transactional
    public ResponseDto<?> addFriendByNickname(FriendAdditionRequestDto friendAdditionRequestDto, HttpServletRequest request) {
        ResponseDto<?> chkResponse = validateCheck(request);
        if(!chkResponse.isSuccess())
            return chkResponse;

        Member member = (Member) chkResponse.getData();
        //nickname으로 검색한 member
        Member findedMember = memberRepository.findByNickname(friendAdditionRequestDto.getValue()).orElse(null);
        if(findedMember == null)
            return ResponseDto.fail("닉네임 친구찾기 실패");

        //Friend type의 객체 생성
        Friend newFriend = Friend.builder()
                .owner(member)
                .friend(findedMember) //사용자가 닉네임 잘 못 입력했을 때, 처리를 해줘야함. isPresnet 사용해서 분기 생성하기.
                .build();

        //새로운 친구 db에 추가.
        friendRepository.save(newFriend);

        return ResponseDto.success(FriendInfoResponseDto.builder()
                        .nickname(newFriend.getFriend().getNickname())
                        .creditScore(newFriend.getFriend().getCredit())
                        .build());
    }

    //휴대전화번호로 친구 추가
    @Transactional
    public ResponseDto<?> addFriendByPhoneNumber(FriendAdditionRequestDto friendAdditionRequestDto, HttpServletRequest request) {
        ResponseDto<?> chkResponse = validateCheck(request);
        if(!chkResponse.isSuccess())
            return chkResponse;

        Member member = (Member) chkResponse.getData();
        //phoneNumber로 검색한 member
        Member findedMember = memberRepository.findByPhoneNumber(friendAdditionRequestDto.getValue()).orElse(null);
        if(findedMember == null)
            return ResponseDto.fail("전화번호 친구찾기 실패");

        //Friend type으로 새 친구 객체 생성
        Friend newFriend = Friend.builder()
                .owner(member)
                .friend(findedMember)
                .build();

        //새로운 친구 db에 추가.
        friendRepository.save(newFriend);

        return ResponseDto.success(FriendInfoResponseDto.builder()
                .nickname(newFriend.getFriend().getNickname())
                .creditScore(newFriend.getFriend().getCredit())
                .build());
    }

    //친구 삭제
    @Transactional
    public ResponseDto<?> deleteFriend(Long friendId, HttpServletRequest request) { //friendId = 친구의 memberId
        ResponseDto<?> chkResponse = validateCheck(request);
        if(!chkResponse.isSuccess())
            return chkResponse;

        Member member = (Member) chkResponse.getData();
        Member findedMember = memberRepository.findById(friendId).get();
        Friend friend = isPresentFriend(member, findedMember);
        if(friend==null)
            return ResponseDto.fail("친구삭제 실패");

        friendRepository.delete(friend);
        return ResponseDto.success("친구삭제가 완료되었습니다.");
    }

    private Friend isPresentFriend(Member owner, Member friend){
        Optional<Friend> findedFriend = friendRepository.findByOwnerAndFriend(owner,friend);
        return findedFriend.orElse(null);
    }

    // 토큰체크
    private ResponseDto<?> validateCheck(HttpServletRequest request) {
        if (null == request.getHeader("RefreshToken") || null == request.getHeader("Authorization")) {
            return ResponseDto.fail("로그인이 필요합니다.");
        }
        Member member = validateMember(request);
        if (null == member) {
            return ResponseDto.fail("Token이 유효하지 않습니다.");
        }
        return ResponseDto.success(member);
    }

    // refreshtoken으로 유저찾기
    @Transactional
    public Member validateMember(HttpServletRequest request) {
        if (!tokenProvider.validateToken(request.getHeader("RefreshToken"))) {
            return null;
        }
        return tokenProvider.getMemberFromAuthentication();
    }
}