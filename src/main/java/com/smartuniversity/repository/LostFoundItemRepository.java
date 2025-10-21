package com.smartuniversity.repository;

import com.smartuniversity.model.LostFoundItem;
import com.smartuniversity.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LostFoundItemRepository extends JpaRepository<LostFoundItem, Long> {
    
    List<LostFoundItem> findByType(LostFoundItem.ItemType type);
    
    List<LostFoundItem> findByStatus(LostFoundItem.ItemStatus status);
    
    List<LostFoundItem> findByCategory(String category);
    
    List<LostFoundItem> findByLocationContainingIgnoreCase(String location);
    
    List<LostFoundItem> findByPostedBy(User user);
    
    List<LostFoundItem> findByTypeAndStatus(LostFoundItem.ItemType type, LostFoundItem.ItemStatus status);
    
    @Query("SELECT i FROM LostFoundItem i WHERE i.status = :status ORDER BY i.dateReported DESC")
    List<LostFoundItem> findActiveItemsOrderByDate(@Param("status") LostFoundItem.ItemStatus status);
    
    @Query("SELECT i FROM LostFoundItem i WHERE " +
           "(LOWER(i.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(i.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
           "AND i.status = :status")
    List<LostFoundItem> searchItems(@Param("searchTerm") String searchTerm, 
                                   @Param("status") LostFoundItem.ItemStatus status);
    
    @Query("SELECT i FROM LostFoundItem i WHERE " +
           "(:type IS NULL OR i.type = :type) AND " +
           "(:category IS NULL OR i.category = :category) AND " +
           "(:location IS NULL OR LOWER(i.location) LIKE LOWER(CONCAT('%', :location, '%'))) AND " +
           "(:searchTerm IS NULL OR LOWER(i.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(i.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) AND " +
           "i.status = :status " +
           "ORDER BY i.dateReported DESC")
    List<LostFoundItem> findItemsWithFilters(@Param("type") LostFoundItem.ItemType type,
                                            @Param("category") String category,
                                            @Param("location") String location,
                                            @Param("searchTerm") String searchTerm,
                                            @Param("status") LostFoundItem.ItemStatus status);
    
    @Query("SELECT COUNT(i) FROM LostFoundItem i WHERE i.type = :type AND i.status = :status")
    Long countByTypeAndStatus(@Param("type") LostFoundItem.ItemType type, 
                             @Param("status") LostFoundItem.ItemStatus status);
    
    @Query("SELECT i FROM LostFoundItem i WHERE i.dateReported < :cutoffDate AND i.status = :status")
    List<LostFoundItem> findExpiredItems(@Param("cutoffDate") LocalDateTime cutoffDate,
                                        @Param("status") LostFoundItem.ItemStatus status);
    
    @Query("SELECT DISTINCT i.category FROM LostFoundItem i WHERE i.status = :status")
    List<String> findDistinctCategories(@Param("status") LostFoundItem.ItemStatus status);
    
    @Query("SELECT DISTINCT i.location FROM LostFoundItem i WHERE i.status = :status")
    List<String> findDistinctLocations(@Param("status") LostFoundItem.ItemStatus status);

    // File management queries
    List<LostFoundItem> findByPostedBy_Id(Long userId);

    Long countByPostedBy_IdAndImageUrlIsNotNull(Long userId);
}