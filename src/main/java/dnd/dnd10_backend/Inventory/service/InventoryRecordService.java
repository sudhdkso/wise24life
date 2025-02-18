package dnd.dnd10_backend.Inventory.service;

import dnd.dnd10_backend.Inventory.domain.InventoryUpdateRecord;
import dnd.dnd10_backend.Inventory.domain.enums.Category;
import dnd.dnd10_backend.Inventory.dto.response.InventoryRecordListResponseDto;
import dnd.dnd10_backend.Inventory.dto.response.InventoryRecordResponseDto;
import dnd.dnd10_backend.Inventory.dto.response.InventoryRecordTodayResponseDto;
import dnd.dnd10_backend.Inventory.repository.InventoryUpdateRecordRepository;
import dnd.dnd10_backend.calendar.domain.TimeCard;
import dnd.dnd10_backend.calendar.repository.TimeCardRepository;
import dnd.dnd10_backend.common.domain.enums.CodeStatus;
import dnd.dnd10_backend.common.exception.CustomerNotFoundException;
import dnd.dnd10_backend.store.domain.Store;
import dnd.dnd10_backend.user.domain.User;
import dnd.dnd10_backend.user.dto.response.UserStoreResponseDto;
import dnd.dnd10_backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 패키지명 dnd.dnd10_backend.Inventory.service
 * 클래스명 InventoryRecordService
 * 클래스설명
 * 작성일 2023-02-11
 *
 * @author 원지윤
 * @version 1.0
 * [수정내용]
 * 예시) [2022-09-17] 주석추가 - 원지윤
 * [2023-02-12] 시재 기록 삭제하는 스케쥴러 추가 - 원지윤
 * [2023-02-12] 시재 기록 조회하는 메소드 추가 - 원지윤
 * [2023-02-14] findInventoryByInventoryName -> findInventoryByStoreAndInventoryName 수정 - 원지윤
 * [2023-02-14] 카테고리별 시재기록 조회 안되는 오류 수정 - 원지윤
 */
@Service
@RequiredArgsConstructor
public class InventoryRecordService {

    private final InventoryUpdateRecordRepository recordRepository;
    private final TimeCardRepository timeCardRepository;
    private final UserService userService;

    /**
     * 시재 기록 조회하는 메소드
     * @param token access token
     */
    public List<InventoryRecordListResponseDto> findInventoryUpdateRecords(Category category, final String token){
        User user = userService.getUserByEmail(token);
        Store store = user.getStore();
        List<InventoryUpdateRecord> list;
        //category가 null이면 전체를 조회
        if(category == null){
            list = recordRepository.findByStore(store);
            return findAllInventoryUpdateRecords(list);
        }

        list = recordRepository.findByStoreAndCategory(store, category);

        return findInventoryUpdateRecordsByCategory(list, category);

    }

    /**
     * 모든 카테고리의 시재 기록을 조회하는 메소드
     * @param list
     * @return
     */
    public List<InventoryRecordListResponseDto> findAllInventoryUpdateRecords(List<InventoryUpdateRecord> list){
        List<InventoryRecordListResponseDto> responseDtoList = list.stream()
                .map(t -> {
                    List<InventoryUpdateRecord> recordList = recordRepository.findByTimeCard(t.getTimeCard());
                    return InventoryRecordListResponseDto.of(t.getUserName(),t.getUserProfileCode(),t.getTimeCard(),convertToInventoryRecordToDto(recordList));
                }).collect(Collectors.toList());
        return responseDtoList;
    }

    /**
     * 카테고리별 시재 기록을 조회하는 메소드
     * @param list 업데이트하려는 시재 기록들의 정보
     * @param category 시재 카테고리 정보
     * @return 응답해주려는 inventoryRecord의 정보
     */
    public List<InventoryRecordListResponseDto> findInventoryUpdateRecordsByCategory(List<InventoryUpdateRecord> list, Category category){
        List<InventoryRecordListResponseDto> responseDtoList = list.stream()
                .map(t -> {
                    List<InventoryUpdateRecord> recordList = recordRepository.findByTimeCardAndCategory(t.getTimeCard(), category);
                    return InventoryRecordListResponseDto.of(t.getUserName(),t.getUserProfileCode(),t.getTimeCard(),convertToInventoryRecordToDto(recordList));
                }).collect(Collectors.toList());
        return responseDtoList;
    }

    /**
     * 오늘 업데이트한 시재 목록들을 조회하는 메소드
     * @param token access token
     * @return 응답해주려는 inventoryRecord 목록
     */
    public List<InventoryRecordTodayResponseDto> findInventoryUpdateRecordToday(final String token) {
        User user = userService.getUserByEmail(token);
        Store store = user.getStore();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul")); // 현재시간

        List<TimeCard> list = timeCardRepository.findByStoreName(store.getStoreName());
        List<InventoryRecordTodayResponseDto> responseDtoList = new ArrayList<>();

        LocalDateTime pointTime = LocalDateTime.parse(now.toLocalDate()+" 00:00:00", formatter);

        for (TimeCard t : list) {
            String[] time = t.getWorkTime().split("~");

            String year = t.getYear();
            String month = t.getMonth().length()<2 ? "0"+t.getMonth() : t.getMonth();
            String day = t.getDay().length()<2 ? "0"+t.getDay() : t.getDay();

            String[] HM1 = time[0].split(":");

            if(HM1[0].equals("24")){
                HM1[0] = "00";
            }

            String[] HM2 = time[1].split(":");

            LocalDateTime startTime = LocalDateTime.parse(year +"-"+month+"-"+day+" "+HM1[0]+":"+HM1[1]+":00", formatter);

            if(HM2[0].equals("24")){
                HM2[0] = "00";
                LocalDateTime plusTime = startTime.plusDays(1);

                day = String.valueOf(plusTime.getDayOfMonth());
                day = day.length() < 2 ? "0"+day : day;

                month = String.valueOf(plusTime.getMonthValue());
                month = month.length() < 2 ? "0"+month : month;

                year = String.valueOf(plusTime.getYear());
            }
            LocalDateTime endTime = LocalDateTime.parse(year +"-"+month+"-"+day+" "+HM2[0]+":"+HM2[1]+":00", formatter);

            if ((pointTime.isBefore(startTime) && pointTime.plusDays(1).isAfter(startTime))|| startTime.isEqual(pointTime)) {


                List<InventoryUpdateRecord> recordList = recordRepository.findByTimeCard(t);

                if(recordList.size() < 1) continue;

                String inventorySummumation = recordList.size() < 2 ? recordList.get(0).getInventoryName() : recordList.get(0).getInventoryName() + " 외 " + String.valueOf(recordList.size()-1);

                responseDtoList.add(InventoryRecordTodayResponseDto.of(
                        recordList.get(0),
                        inventorySummumation
                ));
            }

        }
        Collections.reverse(responseDtoList);
        return responseDtoList;
    }

    /**
     * List<InventoryUpdateRecord>를 List<InventoryRecordResponseDto>로 변환시켜주는 메소드
     * @param recordList 변환시키려는 list
     * @return List타입의 InventoryRecordResponseDto 목록
     */
    public List<InventoryRecordResponseDto> convertToInventoryRecordToDto(List<InventoryUpdateRecord> recordList){
        List<InventoryRecordResponseDto> responseDtoList = recordList.stream()
                .map(t -> InventoryRecordResponseDto.of(t))
                .collect(Collectors.toList());
        return responseDtoList;
    }


    /**
     * 매일 실행되면서 60일 지난 InventoryUpdateRecord 데이터를 찾아 삭제해주는 메소드
     */
    @Scheduled(cron = "0 0 5 * * ?") //매일 오전 5시마다 실행
    public void deletePastRecord(){
        //60일 지난 데이터들을 삭제
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul")); // 현재시간
        LocalDateTime endDateTime = now.minusDays(60); //60일 전 날짜 계산
        List<InventoryUpdateRecord> list = recordRepository.findPastRecord(endDateTime);
        recordRepository.deleteAll(list);
        return;
    }
}
