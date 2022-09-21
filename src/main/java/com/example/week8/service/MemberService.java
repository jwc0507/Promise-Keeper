package com.example.week8.service;

import com.example.week8.domain.LoginMember;
import com.example.week8.domain.Member;
import com.example.week8.domain.enums.Authority;
import com.example.week8.dto.TokenDto;
import com.example.week8.dto.request.AuthRequestDto;
import com.example.week8.dto.request.DuplicationRequestDto;
import com.example.week8.dto.request.LoginRequestDto;
import com.example.week8.dto.response.ResponseDto;
import com.example.week8.repository.LoginMemberRepository;
import com.example.week8.repository.MemberRepository;
import com.example.week8.security.TokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final LoginMemberRepository loginMemberRepository;
    private final MemberRepository memberRepository;
    private final TokenProvider tokenProvider;

    // 인증번호 확인
    private boolean chkValidCode(String phoneNumber, String authCode) {
        // 인증번호 테이블에서 전화번호에 해당하는 인증번호 찾기
        Optional<LoginMember> getLogin = loginMemberRepository.findByPhoneNumber(phoneNumber);
        if (getLogin.isEmpty())
            return false;
        // 여러개의 인증번호가 있을 수 있지만 마지막 값을 찾기 (코드 수정해서 첫 값만 가지게 됨)
        String getAuthCode = getLogin.get().getAuthCode();
        // 입력된 값과 DB의 인증번호가 같은지 확인
        if (!getAuthCode.equals(authCode))
            return false;
        // 인증완료되었다면 인증번호 테이블 비워주기
        loginMemberRepository.deleteById(getLogin.get().getId());

        return true;
    }

    // 회원가입 or 로그인
    @Transactional
    public ResponseDto<?> createMember(LoginRequestDto requestDto, HttpServletResponse response) {
        String phoneNumber = requestDto.getPhoneNumber();
        String authCode = requestDto.getAuthCode();

        // 인증번호 확인
        if (!chkValidCode(phoneNumber, authCode))
            return ResponseDto.fail("인증번호가 다릅니다.");

        // 이미 있는 회원이라면 로그인메소드를 실행시킨다.
        Member member = isPresentMember(requestDto.getPhoneNumber());
        if(member == null) {
            // 없는 회원이라면
            member = Member.builder()
                    .phoneNumber(phoneNumber)
                    .point(1000)
                    .credit(100.0)
                    .password("@")
                    .userRole(Authority.valueOf("ROLE_MEMBER"))
                    .build();
            memberRepository.save(member);
        }
        // 로그인 시키기
        return login(member, response);
    }

    // 로그인하기
    public ResponseDto<?> login(Member member, HttpServletResponse response) {
        TokenDto tokenDto = tokenProvider.generateTokenDto(member);

        tokenToHeaders(tokenDto, response);

        return ResponseDto.success("로그인 완료");
    }

    // 로그아웃
    @Transactional
    public ResponseDto<?> logout(HttpServletRequest request) {
        if(!tokenProvider.validateToken(request.getHeader("RefreshToken")))
            return ResponseDto.fail("토큰 값이 올바르지 않습니다.");

        // 맴버객체 찾아오기
        Member member = tokenProvider.getMemberFromAuthentication();
        if (null == member)
            return ResponseDto.fail("사용자를 찾을 수 없습니다.");
        if (tokenProvider.deleteRefreshToken(member))
            return ResponseDto.fail("존재하지 않는 Token 입니다.");


        return ResponseDto.success("로그아웃 성공");
    }

    // 인증번호 생성(임시)
    @Transactional
    public ResponseDto<?> sendAuthCode(AuthRequestDto requestDto) {
        Optional<LoginMember> getLogin = loginMemberRepository.findByPhoneNumber(requestDto.getValue());
        getLogin.ifPresent(loginMember -> loginMemberRepository.deleteById(loginMember.getId()));
        LoginMember loginMember = LoginMember.builder()
                .authCode(generateCode())
                .phoneNumber(requestDto.getValue())
                .build();
        loginMemberRepository.save(loginMember);

        return ResponseDto.success(loginMember.getAuthCode());
    }

    // SMS 인증번호 발급
    @Transactional
    public ResponseDto<?> sendSMSCode(AuthRequestDto requestDto) {
        return ResponseDto.success("구현안됨");
    }

    // EMAIL 인증번호 발급
    @Transactional
    public ResponseDto<?> sendEmailCode(AuthRequestDto requestDto) {
        return ResponseDto.success("구현안됨");
    }

    private String generateCode() {
        StringBuilder stringBuilder = new StringBuilder();
        for(int i = 0; i < 6; i++) {
            stringBuilder.append((int)Math.floor(Math.random()*10));
        }
        return stringBuilder.toString();
    }

    // 전화번호 중복 검사
    public ResponseDto<?> checkPhoneNumber(DuplicationRequestDto requestDto) {
        Optional<Member> optionalMember = memberRepository.findByPhoneNumber(requestDto.getValue());
        if (optionalMember.isPresent())
            return ResponseDto.fail("중복된 전화번호 입니다.");
        return ResponseDto.success("사용 가능한 전화번호 입니다.");
    }

    // 닉네임 중복 검사
    public ResponseDto<?> checkNickname(DuplicationRequestDto requestDto) {
        Optional<Member> optionalMember = memberRepository.findByNickname(requestDto.getValue());
        if (optionalMember.isPresent())
            return ResponseDto.fail("중복된 닉네임 입니다.");
        return ResponseDto.success("사용 가능한 닉네임 입니다.");
    }

    // 이메일 중복 검사
    public ResponseDto<?> checkEmail(DuplicationRequestDto requestDto) {
        Optional<Member> optionalMember = memberRepository.findByEmail(requestDto.getValue());
        if (optionalMember.isPresent())
            return ResponseDto.fail("사용중인 이메일 입니다.");
        return ResponseDto.success("사용 가능한 이메일 입니다.");
    }

    // 전화번호로 멤버 검색
    @Transactional(readOnly = true)
    public Member isPresentMember(String phoneNumber) {
        Optional<Member> optionalMember = memberRepository.findByPhoneNumber(phoneNumber);
        return optionalMember.orElse(null);
    }

    // 헤더에 토큰담기
    public void tokenToHeaders(TokenDto tokenDto, HttpServletResponse response) {
        response.addHeader("Authorization", "Bearer " + tokenDto.getAccessToken());
        response.addHeader("RefreshToken", tokenDto.getRefreshToken());
    }



}